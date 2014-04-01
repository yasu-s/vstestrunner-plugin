package org.jenkinsci.plugins.vstest_runner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import hudson.console.LineTransformationOutputStream;
import hudson.model.TaskListener;

/**
 * @author a.filatov
 * 31.03.2014.
 */
public class VsTestListenerDecorator extends LineTransformationOutputStream {

    private final static String TRX_PATTERN = "^Results File: (.*\\.trx)$";
    private final static int TRX_GROUP = 1;

    private final static String ATTACHMENTS_PATTERN = "^Attachments:\\s*$";

    private final static String COVERAGE_PATTERN = "^\\s*(.*\\.coverage)$";
    private final static int COVERAGE_GROUP = 1;

    private final OutputStream listener;

    private final Pattern trxPattern;
    private final Pattern attachmentsPattern;
    private final Pattern coveragePattern;

    private boolean attachmentsSection;

    private String trxFile;
    private String coverageFile;

    public VsTestListenerDecorator(TaskListener listener) throws FileNotFoundException {
        this.listener = listener != null ? listener.getLogger() : null;

        trxFile = null;
        coverageFile = null;

        this.attachmentsSection = false;
        this.trxPattern = Pattern.compile(TRX_PATTERN);
        this.attachmentsPattern = Pattern.compile(ATTACHMENTS_PATTERN);
        this.coveragePattern = Pattern.compile(COVERAGE_PATTERN);
    }

    public String getTrxFile()
    {
        return this.trxFile;
    }

    public String getCoverageFile()
    {
        return this.coverageFile;
    }

    @Override
    protected void eol(byte[] bytes, int len) throws IOException {

        if (this.listener == null) {
            return;
        }

        String line = new String(bytes, 0, len);

        Matcher trxMatcher = this.trxPattern.matcher(line);
        if (trxMatcher.find()) {
            this.trxFile = trxMatcher.group(TRX_GROUP);
        }

        if (!this.attachmentsSection) {
            Matcher attachmentsMatcher = this.attachmentsPattern.matcher(line);

            if (attachmentsMatcher.matches()) {
                this.attachmentsSection = true;
            }
        } else {
            Matcher coverageMatcher = this.coveragePattern.matcher(line);
            if (coverageMatcher.find()) {
                this.coverageFile = coverageMatcher.group(COVERAGE_GROUP);
            }
        }

        this.listener.write(line.getBytes());
    }
}
