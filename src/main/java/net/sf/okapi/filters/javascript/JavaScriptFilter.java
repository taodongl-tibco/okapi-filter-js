package net.sf.okapi.filters.javascript;

import net.sf.okapi.common.*;
import net.sf.okapi.common.annotation.GenericAnnotation;
import net.sf.okapi.common.annotation.GenericAnnotationType;
import net.sf.okapi.common.annotation.Note;
import net.sf.okapi.common.annotation.NoteAnnotation;
import net.sf.okapi.common.encoder.EncoderManager;
import net.sf.okapi.common.encoder.JSONEncoder;
import net.sf.okapi.common.exceptions.OkapiBadFilterInputException;
import net.sf.okapi.common.exceptions.OkapiUnsupportedEncodingException;
import net.sf.okapi.common.filters.AbstractFilter;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.filters.SubFilter;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.skeleton.GenericSkeleton;
import net.sf.okapi.common.skeleton.ISkeletonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UsingParameters(Parameters.class)
public class JavaScriptFilter extends AbstractFilter implements JavaScriptHandler {
    private static final String MIMETYPE = "application/javascript";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private boolean hasUtf8Bom;
    private boolean hasUtf8Encoding;
    private Parameters params;
    private JavaScriptEventBuilder eventBuilder;
    private EncoderManager encoderManager;
    private IFilter subFilter;
    private Stack<KeyAndType> keyNames;
    private String currentKeyName;
    private JavaScriptKeyTypes currentKeyType;
    private Pattern exceptions;
    private int subfilterIndex;
    private RawDocument input;
    // Experimental. No nesting supported.
    private NoteAnnotation notes = null;
    private Pattern noteRulesPat = null;
    private Pattern idRulesPat = null;
    private Pattern extractionRulesPat = null;
    private Pattern genericMetaRulesPat = null;
    private Pattern subfilterRulesPat = null;
    private String currentId;
    private List<MetaData> currentGenericMeta;
    private List<ITextUnit> currentTus;
    private Stack<Integer> currentArrayIndex;
    private StringBuilder keylessArrayPath;

    private static class KeyAndType {
        public KeyAndType(String name, JavaScriptKeyTypes type) {
            this.name = name;
            this.type = type;
        }

        String name;
        JavaScriptKeyTypes type;
    }

    private static class MetaData {
        public MetaData(String name, String value) {
            this.name = name;
            this.value = value;
        }

        String name;
        String value;
    }

    public JavaScriptFilter() {
        super();
        currentTus = new LinkedList<>();
        currentArrayIndex = new Stack<>();
        keylessArrayPath = new StringBuilder();
        setMimeType(MIMETYPE);
        setMultilingual(false);
        setName("okf_js"); //$NON-NLS-1$
        setDisplayName("JavaScript Filter"); //$NON-NLS-1$
        addConfiguration(new FilterConfiguration(getName(), MIMETYPE, getClass().getName(),
                "JavaScript", "Configuration for JavaScript files", null, ".js;"));
        setParameters(new Parameters());

        currentGenericMeta = new LinkedList<>();
    }

    @Override
    public void close() {
        super.close();
        hasUtf8Bom = false;
        hasUtf8Encoding = false;
        if (input != null) {
            input.close();
        }
    }

    @Override
    public boolean hasNext() {
        return eventBuilder.hasNext();
    }

    @Override
    public Event next() {
        return eventBuilder.next();
    }

    @Override
    public void open(RawDocument input) {
        open(input, true);
    }

    @Override
    public void open(RawDocument input, boolean generateSkeleton) {
        // save reference for clean up
        this.input = input;

        super.open(input, generateSkeleton);

        BOMNewlineEncodingDetector detector = new BOMNewlineEncodingDetector(input.getStream(), input.getEncoding());
        detector.detectAndRemoveBom();
        String encoding = detector.getEncoding();
        String linebreak = detector.getNewlineType().toString();
        hasUtf8Bom = detector.hasUtf8Bom();
        hasUtf8Encoding = detector.hasUtf8Encoding();
        input.setEncoding(encoding);
        setEncoding(encoding);
        setNewlineType(linebreak);
        setOptions(input.getSourceLocale(), input.getTargetLocale(), encoding, generateSkeleton);

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(detector.getInputStream(), encoding));
        } catch (UnsupportedEncodingException e) {
            throw new OkapiUnsupportedEncodingException(String.format("The encoding '%s' is not supported.", encoding),
                    e);
        }

        if (input.getInputURI() != null) {
            setDocumentName(input.getInputURI().getPath());
        }

        // Pre-compile exceptions or set them to null
        if (Util.isEmpty(params.getExceptions())) {
            exceptions = null;
        } else {
            exceptions = Pattern.compile(params.getExceptions());
        }

        // Pre-compile the regex pattern from the noteRules
        String noteRules = params.getNoteRules();
        if (!noteRules.isEmpty()) {
            noteRulesPat = Pattern.compile(noteRules);
        } else {
            noteRulesPat = null;
        }

        // Pre-compile the regex pattern from the idRules
        String idRules = params.getIdRules();
        if (!idRules.isEmpty()) {
            idRulesPat = Pattern.compile(idRules);
        } else {
            idRulesPat = null;
        }

        // Pre-compile the regex pattern from the extractionRules
        String extractionRules = params.getExtractionRules();
        if (!extractionRules.isEmpty()) {
            extractionRulesPat = Pattern.compile(extractionRules);
        } else {
            extractionRulesPat = null;
        }

        // Pre-compile the regex pattern from the genericMetaRules
        String genericMetaRules = params.getGenericMetaRules();
        if (!genericMetaRules.isEmpty()) {
            genericMetaRulesPat = Pattern.compile(genericMetaRules);
        } else {
            genericMetaRulesPat = null;
        }

        // Pre-compile the regex pattern from the subfilterRules
        String subfilterRules = params.getSubfilterRules();
        if (!subfilterRules.isEmpty()) {
            subfilterRulesPat = Pattern.compile(subfilterRules);
        } else {
            subfilterRulesPat = null;
        }

        // create EventBuilder with document name as rootId
        if (eventBuilder == null) {
            eventBuilder = new JavaScriptEventBuilder(getParentId(), this);
        } else {
            eventBuilder.reset(getParentId(), this);
        }
        eventBuilder.setMimeType(MIMETYPE);
        eventBuilder.setPreserveWhitespace(true);

        // Compile code finder rules
        if (params.getUseCodeFinder()) {
            params.getCodeFinder().compile();
            eventBuilder.setCodeFinder(params.getCodeFinder());
        }

        // Initialize the subfilter
        if (!params.getUseCodeFinder()) {
            String subFilterName = params.getSubfilter();
            if (subFilterName != null && !"".equals(subFilterName)) {
                subFilter = getFilterConfigurationMapper().createFilter(subFilterName, subFilter);
            }
        }
        subfilterIndex = 0;

        keyNames = new Stack<>();
        currentKeyName = null;
        currentKeyType = JavaScriptKeyTypes.DEFAULT;

        JavaScriptVisitor parser = new JavaScriptVisitor(this);
        try {
            parser.visit(reader);
        } catch (Exception e) {
            throw new OkapiBadFilterInputException(String.format("Error parsing JSON file: %s", e.getMessage()), e);
        }
    }

    @Override
    public Parameters getParameters() {
        return params;
    }

    @Override
    public void setParameters(IParameters params) {
        this.params = (Parameters) params;
    }
    @Override
    public ISkeletonWriter createSkeletonWriter () {
        return new JavaScriptSkeletonWriter();
    }
    @Override
    public EncoderManager getEncoderManager() {
        if (encoderManager == null) {
            encoderManager = super.getEncoderManager();
            encoderManager.setMapping(MIMETYPE, "net.sf.okapi.common.encoder.JSONEncoder");
        }
        return encoderManager;
    }

    @Override
    protected boolean isUtf8Encoding() {
        return hasUtf8Encoding;
    }

    @Override
    protected boolean isUtf8Bom() {
        return hasUtf8Bom;
    }

    @Override
    public void handleStart() {
        // add StarDocument event
        setFilterWriter(createFilterWriter());
        eventBuilder.addFilterEvent(createStartFilterEvent());
    }

    @Override
    public void handleEnd() {
        // clear out all temp events
        eventBuilder.flushRemainingTempEvents();
        // add the final endDocument event
        eventBuilder.addFilterEvent(createEndFilterEvent());
    }

    @Override
    public void handleComment(String c) {
        eventBuilder.addDocumentPart(c);
    }

    @Override
    public void handleKey(String key, JavaScriptValueTypes valueType, JavaScriptKeyTypes keyType) {
        eventBuilder.addDocumentPart(String.format("%s%s%s", valueType.getQuoteChar(), key, valueType.getQuoteChar()));
        currentKeyName = key;
        currentKeyType = keyType;
    }

    @Override
    public void handleWhitespace(String whitespace) {
        eventBuilder.addDocumentPart(whitespace);
    }

    @Override
    public void handleValue(String value, JavaScriptValueTypes valueType) {
        // use local values and reset fields
        // cleaner to do it once here
        String key = currentKeyName;
        // Not used: JsonKeyTypes keyType = currentKeyType;
        currentKeyName = null;
        currentKeyType = JavaScriptKeyTypes.DEFAULT;

        if (!params.getExtractStandalone() && key == null) {
            eventBuilder.addDocumentPart(
                    String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
            return;
        }

        // Skip by these value types by default
        switch (valueType) {
            case BOOLEAN:
            case NULL:
            case NUMBER:
            case SYMBOL:
                eventBuilder.addDocumentPart(value);
                return;
            default:
                break;
        }

        // build the unique path to the current value
        String fullPathOrKey = buildKeyPath(key);

        // check if we have an IdRule match
        // only one ID string per extractable string allowed
        if (idRulesPat != null && fullPathOrKey != null) {
            Matcher m = idRulesPat.matcher(fullPathOrKey);
            if (m.matches()) {
                currentId = value;
                eventBuilder.addDocumentPart(
                        String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
                return;
            }
        }

        // check if we have a note
        if (noteRulesPat != null && fullPathOrKey != null) {
            Matcher m = noteRulesPat.matcher(fullPathOrKey);
            if (m.matches()) {
                Note n = new Note(value);
                n.setAnnotates(Note.Annotates.SOURCE);
                n.setFrom(key);

                if (notes == null) {
                    notes = new NoteAnnotation();
                }
                // This will be attached to the next TUs until the closing "}".
                notes.add(n);
                eventBuilder.addDocumentPart(
                        String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
                return;
            }
        }

        // check if we have a genericMetaRule match
        if (genericMetaRulesPat != null && fullPathOrKey != null) {
            Matcher m = genericMetaRulesPat.matcher(fullPathOrKey);
            if (m.matches()) {
                currentGenericMeta.add(new MetaData(fullPathOrKey, value));
                eventBuilder.addDocumentPart(
                        String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
                return;
            }
        }

        // new extraction rules have priority over extraction exceptions
        if (extractionRulesPat != null && fullPathOrKey != null) {
            Matcher m = extractionRulesPat.matcher(fullPathOrKey);
            if (!m.matches()) {
                eventBuilder.addDocumentPart(
                        String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
                return;
            }
        } else {
            // deprecated extraction exceptions
            // if no extraction rules found fall back on
            // old extraction logic
            boolean extract = params.getExtractAllPairs();
            if (exceptions != null && fullPathOrKey != null) {
                if (exceptions.matcher(fullPathOrKey).find()) {
                    // It's an exception, so we reverse the extraction flag
                    extract = !extract;
                }
            }

            if (!extract) { // Not to extract
                eventBuilder.addDocumentPart(
                        String.format("%s%s%s", valueType.getQuoteChar(), value, valueType.getQuoteChar()));
                return;
            }
        }

        if (subFilter != null) {
            boolean shouldSubfilter = true;
            if (subfilterRulesPat != null) {
                shouldSubfilter = subfilterRulesPat.matcher(fullPathOrKey).matches();
            }
            if (shouldSubfilter) {
                callSubfilter(value, valueType, fullPathOrKey);
                return;
            }
        }

        switch (valueType) {
            case DOUBLE_QUOTED_STRING:
            case SINGLE_QUOTED_STRING:
                eventBuilder.startTextUnit(new GenericSkeleton(valueType.getQuoteChar()));
                createTextUnit(value, fullPathOrKey);
                eventBuilder.endTextUnit(new GenericSkeleton(valueType.getQuoteChar()));
                break;
            case SYMBOL:
            case NUMBER:
                eventBuilder.startTextUnit(value);
                createTextUnit(value, fullPathOrKey);
                eventBuilder.endTextUnit();
                break;
            default:
                break;
        }
        logger.debug("KEYNAME: {} : {}", fullPathOrKey, value);
    }

    /*
     * Found an extractable string create a TextUnit
     */
    private void createTextUnit(String value, String key) {
        ITextUnit tu = eventBuilder.peekMostRecentTextUnit();
        if (tu != null) {
            tu.getSource().getFirstContent().append(value);
            // set TU name as key or full path
            // if an ID rule is found we will override this value
            // at the end of the JSON object "}"
            if (params.getUseKeyAsName()) {
                tu.setName(key);
            }
            currentTus.add(tu);
        }
    }

    private void callSubfilter(String value, JavaScriptValueTypes valueType, String parentName) {
        String parentId = eventBuilder.findMostRecentParentId();
        if (parentId == null) {
            parentId = getDocumentId().getLastId();
        }

        // force creation of the parent encoder
        JSONEncoder subEncoder = new JSONEncoder();
        subEncoder.setOptions(params, this.getEncoding(), this.getNewlineType());
        try (SubFilter sf = new SubFilter(subFilter, subEncoder, ++subfilterIndex, parentId, parentName)) {

            // RawDocument closed inside the subfilter call
            List<Event> events = sf.getEvents(new RawDocument(eventBuilder.decode(value), getSrcLoc(), getTrgLoc()));
            eventBuilder.addFilterEvents(events);
            // Now write out the json skeleton
            eventBuilder.addToDocumentPart(valueType.getQuoteChar());
            eventBuilder.addToDocumentPart(sf.createRefCode().toString());
            eventBuilder.addToDocumentPart(valueType.getQuoteChar());

            // get all the TU's in the filter events
            currentTus.addAll(events.stream().filter(e -> e.getEventType() == EventType.TEXT_UNIT)
                    .map(e -> e.getTextUnit()).collect(Collectors.toList()));
        }
    }

    @Override
    public void handleObjectStart() {
        eventBuilder.startGroup(new GenericSkeleton("{"), "Json Object Start");
        keyNames.push(new KeyAndType(currentKeyName, currentKeyType));
        currentKeyName = null;
        currentKeyType = JavaScriptKeyTypes.DEFAULT;
    }

    @Override
    public void handleObjectEnd() {
        // if this object contained an extractable string
        // then check for additional metadata
        // metadata can only be applied if found in a
        // standalone object with an extractable string
        if (!currentTus.isEmpty()) {
            for (ITextUnit tu : currentTus) {
                // ID rule matched use value as name
                if (currentId != null) {
                    tu.setName(currentId);
                }

                if (notes != null) {
                    tu.setAnnotation(notes);
                }

                if (!currentGenericMeta.isEmpty()) {
                    GenericAnnotation a = new GenericAnnotation(GenericAnnotationType.MISC_METADATA);
                    for (MetaData metaData : currentGenericMeta) {
                        a.setString(metaData.name, metaData.value);
                    }
                    GenericAnnotation.addAnnotation(tu, a);
                }
            }
        }

        // reset, can only be used once per object
        notes = null;
        currentId = null;
        currentId = null;
        currentId = null;
        currentGenericMeta.clear();
        currentTus.clear();

        eventBuilder.endGroup(new GenericSkeleton("}"));
        keyNames.pop();
    }

    @Override
    public void handleListStart() {
        // if we are already in an array increment the previous index
        if (!currentArrayIndex.isEmpty()) {
            currentArrayIndex.push(currentArrayIndex.pop() + 1);
        }
        // push the dummy index to indicate the start of an array
        currentArrayIndex.add(-1);
        eventBuilder.startGroup(new GenericSkeleton("["), "Json List Start");
        keyNames.push(new KeyAndType(currentKeyName, currentKeyType));
        currentKeyName = null;
        currentKeyType = JavaScriptKeyTypes.DEFAULT;
    }

    @Override
    public void handleListEnd() {
        currentArrayIndex.pop();
        eventBuilder.endGroup(new GenericSkeleton("]"));
        keyNames.pop();
    }

    @Override
    public void handleSeparator(String separator) {
        eventBuilder.addDocumentPart(separator);
    }

    // build the full path of the key if wanted
    // otherwise return the original key
    private String buildKeyPath(String key) {
        StringBuilder keyPath = new StringBuilder();

        if (!params.getUseFullKeyPath()) {
            // all values in a list use the immediate parent list key name
            if (!keyNames.isEmpty() && keyNames.peek().type == JavaScriptKeyTypes.LIST) {
                return keyNames.peek().name;
            }
            return key;
        }

        // if we don't have a key, and we are inside an array
        // it must be a keyless array value. Use its index value to create a key
        if (key == null && !currentArrayIndex.isEmpty()) {
            StringBuilder arrayKey = new StringBuilder();
            // increment array index
            currentArrayIndex.push(currentArrayIndex.pop() + 1);
            arrayKey.append("array:");
            Iterator<Integer> it = currentArrayIndex.listIterator();
            Integer k = it.next();
            arrayKey.append(k);
            while (it.hasNext()) {
                k = it.next();
                arrayKey.append("/array:");
                arrayKey.append(k);
            }
            key = arrayKey.toString();
        }

        if (!keyNames.isEmpty()) {
            Iterator<KeyAndType> it = keyNames.listIterator();
            while (it.hasNext()) {
                KeyAndType k = it.next();
                if (k != null && k.name != null) {
                    keyPath.append("/").append(k.name);
                }
            }
        }

        if (key != null && !key.isEmpty()) {
            keyPath.append("/").append(key);
        }

        if (!params.getUseLeadingSlashOnKeyPath()) {
            if (keyPath.charAt(0) == '/') {
                keyPath.deleteCharAt(0);
            }
        }

        return keyPath.toString();
    }
}
