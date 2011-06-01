package jar2xml;

import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;

public class AndroidDocScraper implements IDocScraper {

	File root;

	public AndroidDocScraper (File dir) throws IOException {

		if (dir == null)
			throw new IllegalArgumentException ();

		if (!dir.exists())
			throw new FileNotFoundException (dir.getAbsolutePath());

		if (!dir.isDirectory())
			throw new IllegalArgumentException (dir.getAbsolutePath() + " is not a directory.");

		root = dir;

		File packageList = new File (dir.getAbsolutePath() + "/package-list");
		if (!packageList.isFile())
			throw new IllegalArgumentException (dir.getAbsolutePath() + " does not appear to be an android doc reference directory.");
	}


	String doc_content;
	Class cached_class;

	public String[] getParameterNames (Class declarer, String name, Type[] ptypes)
	{
		String path = declarer.getName ().replace('.', '/').replace ('$', '.') + ".html";
		if (cached_class != declarer) {
			File file = new File(root.getPath() + "/" + path);
			if (!file.isFile ())
				return null;
			FileInputStream stream = null;
			StringBuffer html_buffer = new StringBuffer ();
			try {
				stream = new FileInputStream (file);
				InputStreamReader rdr;
				rdr = new InputStreamReader (stream, "UTF-8");
				BufferedReader br = new BufferedReader (rdr);
				String text;
				while ((text = br.readLine ()) != null) {
					html_buffer.append (text);
					html_buffer.append ("\n");
				}
				stream.close();
			} catch (Exception e) {
				return new String [0];
			}

			cached_class = declarer;
			doc_content = html_buffer.toString ();
		}

		StringBuffer buffer = new StringBuffer ();
		buffer.append ("<span class=\"sympad\"><a href=\".*");
		buffer.append (path);
		buffer.append ("#");
		buffer.append (name);
		buffer.append ("\\(\\Q");
		for (int i = 0; i < ptypes.length; i++) {
			if (i != 0)
				buffer.append (", ");
			buffer.append (JavaClass.getGenericTypeName (ptypes[i]));
		}
		buffer.append("\\E\\)\".*\\((.*)\\)");

		Pattern pattern = Pattern.compile (buffer.toString());
		Matcher matcher = pattern.matcher (doc_content);
		if (matcher.find ()) {
			String plist = matcher.group (1);
			String[] parms = plist.split (", ");
			if (parms.length != ptypes.length)
				System.err.println ("failed matching " + buffer.toString ());
			String[] result = new String [ptypes.length];
			for (int i = 0; i < ptypes.length; i++) {
				String[] toks = parms [i].split ("\\s+");
				result [i] = toks [toks.length - 1];
			}
			return result;
		}
		return new String [0];
/*
		System.err.println ("found " + regex.toString ());
		// found it. Lookup the parameter names.
		String[] names = new String[types.length];
		// now we're sure we have the right method, find the parameter names!
		String regexParams = "<DD><CODE>([^<]*)</CODE>";
		Pattern patternParams = Pattern.compile(regexParams);
		int start = matcher.end();
		Matcher matcherParams = patternParams.matcher(javadoc);
		for (int i = 0; i < types.length; i++) {
			boolean find = matcherParams.find(start);
			if (!find)
				return Paranamer.EMPTY_NAMES;
			start = matcherParams.end();
			names[i] = matcherParams.group(1);
		}
		return names;
*/
	}
}
