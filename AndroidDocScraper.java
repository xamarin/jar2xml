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
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.objectweb.asm.tree.*;

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

	public String[] getParameterNames (ClassNode asm, String name, Type[] ptypes, boolean isVarArgs)
	{
		String path = asm.name.replace ('$', '.') + ".html";
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
			String type = JavaClass.getGenericTypeName (ptypes[i]);
			if (isVarArgs && i == ptypes.length - 1)
				type = type.replace ("[]", "...");
			buffer.append (type);
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
	
	static Map<String,List<String>> deprecatedFields;
	static Map<String,List<String>> deprecatedMethods;
	
	public static void loadXml (String filename)
	{
		try {

			Document doc = DocumentBuilderFactory.newInstance ().newDocumentBuilder ().parse (filename);
			deprecatedFields = new HashMap<String,List<String>> ();
			deprecatedMethods = new HashMap<String,List<String>> ();
			NodeList files = doc.getDocumentElement ().getElementsByTagName ("file");
			for (int i = 0; i < files.getLength (); i++) {
				Element file = (Element) files.item (i);
				ArrayList<String> f = new ArrayList<String> ();
				deprecatedFields.put (file.getAttribute ("name"), f);
				NodeList fields = file.getElementsByTagName ("field");
				for (int j = 0; j < fields.getLength (); j++)
					f.add (fields.item (j).getTextContent ());

				ArrayList<String> m = new ArrayList<String> ();
				deprecatedMethods.put (file.getAttribute ("name"), m);
				NodeList methods = file.getElementsByTagName ("method");
				for (int j = 0; j < methods.getLength (); j++)
					m.add (methods.item (j).getTextContent ());
			}
		
		} catch (Exception ex) {
			System.err.println ("Annotations parser error: " + ex);
		}
	}
	
	public static List<String> getDeprecatedFields (ClassNode asm)
	{
		if (deprecatedFields == null)
			return null;
		return deprecatedFields.get (asm.name.replace ('$', '.'));
	}
	
	public static List<String> getDeprecatedMethods (ClassNode asm)
	{
		if (deprecatedMethods == null)
			return null;
		return deprecatedMethods.get (asm.name.replace ('$', '.'));
	}
}
