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

import java.io.File;
import java.io.FileWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Start {

	public static void main (String[] args)
	{
		String docs = null;
		String jar_path = null;
		String out_path = null;
		String usage = "Usage: jar2xml --jar=<jarfile> --out=<file> [--docpath=<javadocs>]";

		for (String arg : args) {
			if (arg.startsWith ("--docpath=")) {
				docs = arg.substring (10);
			} else if (arg.startsWith ("--jar=")) {
				jar_path = arg.substring (6);
			} else if (arg.startsWith ("--out=")) {
				out_path = arg.substring (6);
			} else {
				System.err.println (usage);
				System.exit (1);
			}
		}

		if (jar_path == null || out_path == null) {
			System.err.println (usage);
			System.exit (1);
		}

		JavaArchive jar = null;
		try {
			jar = new JavaArchive (jar_path);
		} catch (Exception e) {
			System.err.println ("Couldn't open java archive at specified path " + jar_path);
			System.exit (1);
		}

		try {
			if (docs != null)
				JavaClass.addDocScraper (new AndroidDocScraper (new File (docs)));
		} catch (Exception e) {
			System.err.println ("Couldn't access javadocs at specified docpath.  Continuing without it...");
		}

		Document doc = null;
		try {
			DocumentBuilderFactory builder_factory = DocumentBuilderFactory.newInstance ();
			DocumentBuilder builder = builder_factory.newDocumentBuilder ();
			doc = builder.newDocument ();
		} catch (Exception e) {
			System.err.println ("Couldn't create xml document - exception occurred:" + e.getMessage ());
		}

		Element root = doc.createElement ("api");
		doc.appendChild (root);
		for (JavaPackage pkg : jar.getPackages ())
			pkg.appendToDocument (doc, root);

		try {
			TransformerFactory transformer_factory = TransformerFactory.newInstance ();
			Transformer transformer = transformer_factory.newTransformer ();
			transformer.setOutputProperty (OutputKeys.INDENT, "yes");
			FileWriter writer = new FileWriter (new File (out_path));
			StreamResult result = new StreamResult (writer);
			DOMSource source = new DOMSource (doc);
			transformer.transform (source, result);
		} catch (Exception e) {
			System.err.println ("Couldn't format xml file - exception occurred:" + e.getMessage ());
		}
	}
}

