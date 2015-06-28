package ut.com.atlassian.jgitflow.core;

import com.atlassian.jgitflow.core.TaggedVersion;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TaggedVersionTest {

	@Test
	public void testComparison() throws Exception {
		assertTrue(compare("1.0", "1.1") < 0);
		assertTrue(compare("1.1", "1.0") > 0);
		assertTrue(compare("1.0", "1.0") == 0);
		assertTrue(compare("1.0.1", "1.0.0") > 0);
		assertTrue(compare("1.0.1", "1.0") > 0);
		assertTrue(compare("1.0.0", "1.0") > 0);
		assertTrue(compare("2.0.14", "1.5.0") > 0);
		assertTrue(compare("2.3.4", "1.9.5") > 0);
		assertTrue(compare("2.1.3", "2.0.7") > 0);
		assertTrue(compare("2.4.8", "2.4.7") > 0);
		assertTrue(compare("2", "1.3.0") > 0);
		assertTrue(compare("2.0.0", "1.3") > 0);
		assertTrue(compare("0.12", "0.9") > 0);
		assertTrue(compare("15.0", "3.0") > 0);
	}

	private int compare(String version1, String version2) throws VersionParseException {
		return new TaggedVersion(version1, null).compareTo(new TaggedVersion(version2, null));
	}
}

