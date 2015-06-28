package com.atlassian.jgitflow.core;

import org.apache.maven.shared.release.versions.VersionParseException;
import org.eclipse.jgit.lib.Ref;

public class TaggedVersion implements Comparable<TaggedVersion> {

	private final ReleaseVersionInfo version;
	private final Ref tag;

	public TaggedVersion(String version, Ref tag) throws VersionParseException {
		this.version = new ReleaseVersionInfo(version);
		this.tag = tag;
	}

	@Override
	public int compareTo(TaggedVersion that) {
		return version.compareTo(that.version);
	}

	public Ref getTag() {
		return tag;
	}
}
