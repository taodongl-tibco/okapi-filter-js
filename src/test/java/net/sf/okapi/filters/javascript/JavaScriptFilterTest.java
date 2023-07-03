package net.sf.okapi.filters.javascript;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.EventType;
import net.sf.okapi.common.IResource;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.filterwriter.IFilterWriter;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.resource.TextContainer;
import net.sf.okapi.common.resource.TextFragment;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

public class JavaScriptFilterTest {
    @Test
    public void testCemResourceFile() throws URISyntaxException {
        IFilter filter = new JavaScriptFilter();
        IFilterWriter writer = filter.createFilterWriter();
        LocaleId tgtLocaleId = LocaleId.fromString("ja-JP");
        writer.setOptions(tgtLocaleId, "UTF-8");
        URL resource = getClass().getResource("/cem.js");
        File srcPath = new File(resource.toURI());
        File tgtPath = new File(srcPath.getParentFile(), "cem-ja-JP.js");
        writer.setOutput(tgtPath.toString());
        RawDocument rawDocument = new RawDocument(Objects.requireNonNull(getClass().getResourceAsStream("/cem.js")), "UTF-8", new LocaleId("en"));
        filter.open(rawDocument);
        while (filter.hasNext()) {

            Event event = filter.next();

            if (event.getEventType() == EventType.TEXT_UNIT) {

                ITextUnit tu = event.getTextUnit();

                // Get ID
                System.out.printf("%nID=%s", tu.getId());

                // Get Key
                System.out.printf("%nKey=%s", tu.getName());

                // Get Value
                System.out.printf("%nValue=%s", tu.getSource().getFirstContent().toText());




                TextContainer tc = tu.createTarget(tgtLocaleId, true, IResource.COPY_ALL);
                TextFragment tf = tc.getFirstContent();
                tf.setCodedText("開始-" + tf.getCodedText().toUpperCase() + "-終了");

                // Get Target String
                System.out.printf("%nTarget Value=%s", tu.getTarget(tgtLocaleId).getCodedText());

                System.out.println();
            }

            writer.handleEvent(event);

        }

        // Close the input document
        filter.close();
        writer.close();
    }
}
