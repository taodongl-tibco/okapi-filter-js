package net.sf.okapi.filters.javascript;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.Mockito.mock;

public class JavaScriptVisitorTest {
    @Test
    public void testCemResourceFile() throws URISyntaxException, IOException {
        JavaScriptHandler handler = mock(JavaScriptHandler.class);
        JavaScriptVisitor visitor = new JavaScriptVisitor(handler);
        URL resource = getClass().getResource("/cem.js");
        InputStreamReader reader = new InputStreamReader(Files.newInputStream(Paths.get(resource.toURI())));
        visitor.visit(reader);
    }

}
