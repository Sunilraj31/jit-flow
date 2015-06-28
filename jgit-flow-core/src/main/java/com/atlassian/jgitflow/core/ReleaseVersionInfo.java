package com.atlassian.jgitflow.core;

import jdk.nashorn.internal.runtime.Version;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.shared.release.versions.VersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReleaseVersionInfo<T extends ReleaseVersionInfo<T>> implements VersionInfo {

    protected final String strVersion;

    protected final List<String> digits;

    protected String annotation;

    protected String annotationRevision;

    protected final String buildSpecifier;

    protected String annotationSeparator;

    protected String annotationRevSeparator;

    protected final String buildSeparator;

    private static final int DIGITS_INDEX = 1;

    private static final int ANNOTATION_SEPARATOR_INDEX = 2;

    private static final int ANNOTATION_INDEX = 3;

    private static final int ANNOTATION_REV_SEPARATOR_INDEX = 4;

    private static final int ANNOTATION_REVISION_INDEX = 5;

    private static final int BUILD_SEPARATOR_INDEX = 6;

    private static final int BUILD_SPECIFIER_INDEX = 7;

    private static final String SNAPSHOT_IDENTIFIER = "SNAPSHOT";

    private static final String DIGIT_SEPARATOR_STRING = ".";

    public static final Pattern STANDARD_PATTERN = Pattern.compile(
            "^((?:\\d+\\.)*\\d+)"      // digit(s) and '.' repeated - followed by digit (version digits 1.22.0, etc)
                    + "([-_])?"                // optional - or _  (annotation separator)
                    + "([a-zA-Z]*)"            // alpha characters (looking for annotation - alpha, beta, RC, etc.)
                    + "([-_])?"                // optional - or _  (annotation revision separator)
                    + "(\\d*)"                 // digits  (any digits after rc or beta is an annotation revision)
                    + "(?:([-_])?(.*?))?$"
    );  // - or _ followed everything else (build specifier)

    /* *
     * cmaki 02242009
     * FIX for non-digit release numbers, e.g. trunk-SNAPSHOT or just SNAPSHOT
     * This alternate pattern supports version numbers like:
     * trunk-SNAPSHOT
     * branchName-SNAPSHOT
     * SNAPSHOT
     */
    public static final Pattern ALTERNATE_PATTERN = Pattern.compile(
            "^(SNAPSHOT|[a-zA-Z]+[_-]SNAPSHOT)"      // for SNAPSHOT releases only (possible versions include: trunk-SNAPSHOT or SNAPSHOT)
    );

    /**
     * Constructs this object and parses the supplied version string.
     *
     * @param version
     */
    public ReleaseVersionInfo(String version)
            throws VersionParseException
    {
        strVersion = version;

        // FIX for non-digit release numbers, e.g. trunk-SNAPSHOT or just SNAPSHOT
        Matcher matcher = ALTERNATE_PATTERN.matcher(strVersion);
        // TODO: hack because it didn't support "SNAPSHOT"
        if (matcher.matches())
        {
            annotation = null;
            digits = null;
            buildSpecifier = version;
            buildSeparator = null;
            return;
        }

        Matcher m = STANDARD_PATTERN.matcher(strVersion);
        if (m.matches())
        {
            digits = parseDigits(m.group(DIGITS_INDEX));
            if (!SNAPSHOT_IDENTIFIER.equals(m.group(ANNOTATION_INDEX)))
            {
                annotationSeparator = m.group(ANNOTATION_SEPARATOR_INDEX);
                annotation = nullIfEmpty(m.group(ANNOTATION_INDEX));

                if (StringUtils.isNotEmpty(m.group(ANNOTATION_REV_SEPARATOR_INDEX))
                        && StringUtils.isEmpty(m.group(ANNOTATION_REVISION_INDEX)))
                {
                    // The build separator was picked up as the annotation revision separator
                    buildSeparator = m.group(ANNOTATION_REV_SEPARATOR_INDEX);
                    buildSpecifier = nullIfEmpty(m.group(BUILD_SPECIFIER_INDEX));
                }
                else
                {
                    annotationRevSeparator = m.group(ANNOTATION_REV_SEPARATOR_INDEX);
                    annotationRevision = nullIfEmpty(m.group(ANNOTATION_REVISION_INDEX));

                    buildSeparator = m.group(BUILD_SEPARATOR_INDEX);
                    buildSpecifier = nullIfEmpty(m.group(BUILD_SPECIFIER_INDEX));
                }
            }
            else
            {
                // Annotation was "SNAPSHOT" so populate the build specifier with that data
                buildSeparator = m.group(ANNOTATION_SEPARATOR_INDEX);
                buildSpecifier = nullIfEmpty(m.group(ANNOTATION_INDEX));
            }
        }
        else
        {
            throw new VersionParseException("Unable to parse the version string: \"" + version + "\"");
        }
    }

    /**
     * Compares this {@link ReleaseVersionInfo} to the supplied {@link ReleaseVersionInfo}
     * to determine which version is greater.
     *
     * @param obj the comparison version
     * @return the comparison value
     * @throws IllegalArgumentException if the components differ between the objects or if either of the annotations can not be determined.
     */
    @Override
    public int compareTo(VersionInfo obj)
    {
        T that = (T) obj;

        int result;
        // TODO: this is a workaround for a bug in DefaultArtifactVersion - fix there - 1.01 < 1.01.01
        if (strVersion.startsWith(that.strVersion) && !strVersion.equals(that.strVersion)
                && strVersion.charAt(that.strVersion.length()) != '-')
        {
            result = 1;
        }
        else if (that.strVersion.startsWith(strVersion) && !strVersion.equals(that.strVersion)
                && that.strVersion.charAt(strVersion.length()) != '-')
        {
            result = -1;
        }
        else
        {
            // TODO: this is a workaround for a bug in DefaultArtifactVersion - fix there - it should not consider case in comparing the qualifier
            // NOTE: The combination of upper-casing and lower-casing is an approximation of String.equalsIgnoreCase()
            String thisVersion = strVersion.toUpperCase(Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
            String thatVersion = that.strVersion.toUpperCase(Locale.ENGLISH).toLowerCase(Locale.ENGLISH);

            result = new DefaultArtifactVersion(thisVersion).compareTo(new DefaultArtifactVersion(thatVersion));
        }
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!getClass().isInstance(obj))
        {
            return false;
        }

        return compareTo((VersionInfo) obj) == 0;
    }

    @Override
    public String toString()
    {
        return strVersion;
    }

    /**
     * Splits the string on "." and returns a list
     * containing each digit.
     *
     * @param strDigits
     */
    private List<String> parseDigits(String strDigits)
    {
        return Arrays.asList(StringUtils.split(strDigits, DIGIT_SEPARATOR_STRING));
    }

    //--------------------------------------------------
    // Getters & Setters
    //--------------------------------------------------

    private static String nullIfEmpty(String s)
    {
        return StringUtils.isEmpty(s) ? null : s;
    }

    @Override
    public boolean isSnapshot() {
        throw new UnsupportedOperationException("This method should not be used");
    }

    @Override
    public String getSnapshotVersionString() {
        throw new UnsupportedOperationException("This method should not be used");
    }

    @Override
    public String getReleaseVersionString() {
        throw new UnsupportedOperationException("This method should not be used");
    }

    @Override
    public VersionInfo getNextVersion() {
        throw new UnsupportedOperationException("This method should not be used");
    }
}
