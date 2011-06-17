/* 
 *  Copyright (c) 2011 Xamarin Inc.
 * 
 *  Permission is hereby granted, free of charge, to any person 
 *  obtaining a copy of this software and associated documentation 
 *  files (the "Software"), to deal in the Software without restriction, 
 *  including without limitation the rights to use, copy, modify, merge, 
 *  publish, distribute, sublicense, and/or sell copies of the Software, 
 *  and to permit persons to whom the Software is furnished to do so, 
 *  subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be 
 *  included in all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES 
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND 
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS 
 *  BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN 
 *  ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
 *  SOFTWARE.
 */

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

	public String[] getParameterNames (Class declarer, String name, Type[] ptypes)
	{
		String path = declarer.getName ().replace('.', '/').replace ('$', '.') + ".html";
		File file = new File(root.getPath() + "/" + path);
		if (!file.isFile ())
			return null;

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

		try {
			FileInputStream stream = new FileInputStream (file);
			InputStreamReader rdr;
			rdr = new InputStreamReader (stream, "UTF-8");
			BufferedReader br = new BufferedReader (rdr);
			String text;
			while ((text = br.readLine ()) != null) {
				Matcher matcher = pattern.matcher (text);
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
			}
			stream.close();
		} catch (Exception e) {
			return new String [0];
		}

		return new String [0];
	}
}
