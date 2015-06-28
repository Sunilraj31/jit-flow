package org.apache.maven.shared.release.version;

/*
 * This is a modified version of DefaultVersionInfo from the maven-release-manager
 * project that adds support for creating hotfix versions
 * The original license is as follows:
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.jgitflow.core.BaseVersionInfo;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.shared.release.versions.VersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.util.StringUtils;

public class HotfixVersionInfo extends BaseVersionInfo
{

    /**
     * Constructs this object and parses the supplied version string.
     *
     * @param version
     */
    public HotfixVersionInfo(String version)
            throws VersionParseException
    {
        super(version);
    }

    public HotfixVersionInfo(List<String> digits, String annotation, String annotationRevision, String buildSpecifier,
                             String annotationSeparator, String annotationRevSeparator, String buildSeparator)
    {
        this.digits = digits;
        this.annotation = annotation;
        this.annotationRevision = annotationRevision;
        this.buildSpecifier = buildSpecifier;
        this.annotationSeparator = annotationSeparator;
        this.annotationRevSeparator = annotationRevSeparator;
        this.buildSeparator = buildSeparator;
        this.strVersion = getVersionString(this, buildSpecifier, buildSeparator);
    }

    public boolean isSnapshot()
    {
        return ArtifactUtils.isSnapshot(strVersion);
    }

    public VersionInfo getNextVersion()
    {
        HotfixVersionInfo version = null;
        if (digits != null)
        {
            List<String> digits = new ArrayList<String>(this.digits);
            String annotationRevision = this.annotationRevision;
            if (StringUtils.isNumeric(annotationRevision))
            {
                annotationRevision = incrementVersionString(annotationRevision);
            }
            else
            {
                digits.set(digits.size() - 1, incrementVersionString((String) digits.get(digits.size() - 1)));
            }

            version = new HotfixVersionInfo(digits, annotation, annotationRevision, buildSpecifier,
                    annotationSeparator, annotationRevSeparator, buildSeparator);
        }
        return version;
    }

    /**
     * Takes a string and increments it as an integer.
     * Preserves any lpad of "0" zeros.
     *
     * @param s
     */
    protected String incrementVersionString(String s)
    {
        int n = Integer.valueOf(s).intValue() + 1;
        String value = String.valueOf(n);
        if (value.length() < s.length())
        {
            // String was left-padded with zeros
            value = StringUtils.leftPad(value, s.length(), "0");
        }
        return value;
    }

    public String getSnapshotVersionString()
    {
        if (strVersion.equals(Artifact.SNAPSHOT_VERSION))
        {
            return strVersion;
        }

        String baseVersion = getReleaseVersionString();

        if (baseVersion.length() > 0)
        {
            baseVersion += "-";
        }

        return baseVersion + Artifact.SNAPSHOT_VERSION;
    }

    public String getReleaseVersionString()
    {
        String baseVersion = strVersion;

        Matcher m = Artifact.VERSION_FILE_PATTERN.matcher(baseVersion);
        if (m.matches())
        {
            baseVersion = m.group(1);
        }
        // MRELEASE-623 SNAPSHOT is case-insensitive
        else if (StringUtils.right(baseVersion, 9).equalsIgnoreCase("-" + Artifact.SNAPSHOT_VERSION))
        {
            baseVersion = baseVersion.substring(0, baseVersion.length() - Artifact.SNAPSHOT_VERSION.length() - 1);
        }
        else if (baseVersion.equals(Artifact.SNAPSHOT_VERSION))
        {
            baseVersion = "1.0";
        }
        return baseVersion;
    }

    //if starting at 1.1, returns 1.1.1
    public String getHotfixVersionString()
    {
        HotfixVersionInfo version = null;
        if (digits != null)
        {
            List<String> digits = new ArrayList<String>(this.digits);

            // modECG
            // dont know how but will progress that
            if (digits.size() == 0)
            {
                digits.add("0");
                digits.add("0");
                digits.add("1");
            }
            // found majorVersion
            else if (digits.size() == 1)
            {
                digits.add("0");
                digits.add("1");
            }
            // found minorVersion (standard)
            else if (digits.size() == 2)
            {
                digits.add("1");
            }
            // found bugfixVersion
            else if (digits.size() == 3)
            {
                int newBugfixDigit = Integer.parseInt(digits.get(2)) + 1;
                digits.set(2, String.valueOf(newBugfixDigit));
            }
            // found more than that
            else
            {
                // not a real versioning scheme so add some humbug
                digits.add("hotfix");
            }

            version = new HotfixVersionInfo(digits, annotation, annotationRevision, buildSpecifier, annotationSeparator, annotationRevSeparator, buildSeparator);
        }

        return version.getReleaseVersionString();
    }

    //if starting at 1.1, returns 1.0.1
    public String getDecrementedHotfixVersionString()
    {
        HotfixVersionInfo version = null;
        if (digits != null)
        {
            List<String> digits = new ArrayList<String>(this.digits);

            String lastDigit = digits.get(digits.size() - 1);
            int n = Integer.valueOf(lastDigit).intValue();

            if (n > 0)
            {
                digits.set(digits.size() - 1, Integer.toString(n - 1));
                digits.add("1");
            }
            else
            {
                digits.set(digits.size() - 1, "1");
            }

            version = new HotfixVersionInfo(digits, annotation, annotationRevision, buildSpecifier, annotationSeparator, annotationRevSeparator, buildSeparator);
        }
        return version.getReleaseVersionString();
    }

    protected static String getVersionString(HotfixVersionInfo info, String buildSpecifier, String buildSeparator)
    {
        StringBuilder sb = new StringBuilder();

        if (info.digits != null)
        {
            sb.append(joinDigitString(info.digits));
        }

        if (StringUtils.isNotEmpty(info.annotation))
        {
            sb.append(StringUtils.defaultString(info.annotationSeparator));
            sb.append(info.annotation);
        }

        if (StringUtils.isNotEmpty(info.annotationRevision))
        {
            if (StringUtils.isEmpty(info.annotation))
            {
                sb.append(StringUtils.defaultString(info.annotationSeparator));
            }
            else
            {
                sb.append(StringUtils.defaultString(info.annotationRevSeparator));
            }
            sb.append(info.annotationRevision);
        }

        if (StringUtils.isNotEmpty(buildSpecifier))
        {
            sb.append(StringUtils.defaultString(buildSeparator));
            sb.append(buildSpecifier);
        }

        return sb.toString();
    }

    /**
     * Simply joins the items in the list with "." period
     *
     * @param digits
     */
    protected static String joinDigitString(List<String> digits)
    {
        return digits != null ? StringUtils.join(digits.iterator(), DIGIT_SEPARATOR_STRING) : null;
    }

    //--------------------------------------------------
    // Getters & Setters
    //--------------------------------------------------

    public List<String> getDigits()
    {
        return digits;
    }

    public String getAnnotation()
    {
        return annotation;
    }

    public String getAnnotationRevision()
    {
        return annotationRevision;
    }

    public String getBuildSpecifier()
    {
        return buildSpecifier;
    }

}
