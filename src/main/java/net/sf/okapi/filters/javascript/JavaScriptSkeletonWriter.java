package net.sf.okapi.filters.javascript;

import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.encoder.EncoderContext;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.skeleton.GenericSkeleton;
import net.sf.okapi.common.skeleton.GenericSkeletonPart;
import net.sf.okapi.common.skeleton.GenericSkeletonWriter;

import java.util.List;

public class JavaScriptSkeletonWriter extends GenericSkeletonWriter {
    protected String getString(ITextUnit tu, LocaleId locToUse, EncoderContext context) {
        if (!context.equals(EncoderContext.TEXT)) {
            return super.getString(tu, locToUse, context);
        }
        GenericSkeleton skel = (GenericSkeleton) tu.getSkeleton();
        if ( skel == null ) { // No skeleton
            return getContent(tu, locToUse, context);
        }
        StringBuilder tmp = new StringBuilder();

        List<GenericSkeletonPart> parts = skel.getParts();
        int count = parts.size();
        if (count == 0) {
            return "";
        }
        GenericSkeletonPart part = parts.get(0);
        String mark = getString(part, context);
        tmp.append(mark);
        for (int i = 1; i < count - 1; i++) {
            part = parts.get(i);
            String text = getString(part, context);
            if (mark.equals("'")) {
                text = text.replace("'", "\\'").replace("\\\"", "\"");
            }
            tmp.append(text);
        }
        tmp.append(getString(parts.get(count - 1), context));
        return tmp.toString();
    }
}
