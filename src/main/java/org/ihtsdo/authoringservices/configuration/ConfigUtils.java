package org.ihtsdo.authoringservices.configuration;

import java.util.HashSet;
import java.util.Set;

public class ConfigUtils {
	public static Set<String> getStringSet(String commaSeparatedList) {
		Set<String> values = new HashSet<>();
		if (commaSeparatedList != null) {
			String[] split = commaSeparatedList.split(",");
			for (String s : split) {
				s = s.trim();
				if (!s.isEmpty()) {
					values.add(s);
				}
			}
		}
		return values;
	}
}
