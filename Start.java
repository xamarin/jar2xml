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
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Start {

	// FIXME: for future compatibility, they had better be scraped
	// from java/lang/annotated/Annotation.html (known *direct subclasses)
	static final 	String [] annotations = {
		"android.test.FlakyTest",
		"android.test.UiThreadTest",
		"android.test.suitebuilder.annotation.LargeTest",
		"android.test.suitebuilder.annotation.MediumTest",
		"android.test.suitebuilder.annotation.SmallTest",
		"android.test.suitebuilder.annotation.Smoke",
		"android.test.suitebuilder.annotation.Suppress",
		"android.view.ViewDebug$CapturedViewProperty",
		"android.view.ViewDebug$ExportedProperty",
		"android.view.ViewDebug$FlagToString",
		"android.view.ViewDebug$IntToString",
		"android.widget.RemoteViews$RemoteView",
		"dalvik.annotation.TestTarget",
		"dalvik.annotation.TestTargetClass",
		"java.lang.Deprecated",
		"java.lang.Override",
		"java.lang.SuppressWarnings",
		"java.lang.annotation.Documented",
		"java.lang.annotation.Inherited",
		"java.lang.annotation.Retention",
		"java.lang.annotation.Target",
		"java.lang.annotation.Documented"
		};

	static Element createAnnotationMock (Document doc, String name)
	{
		Element e = doc.createElement ("class");
		e.setAttribute ("abstract", "true");
		e.setAttribute ("deprecated", "not deprecated");
		e.setAttribute ("extends", "java.lang.Object");
		e.setAttribute ("final", "false");
		e.setAttribute ("name", name);
		e.setAttribute ("static", "false");
		e.setAttribute ("visibility", "public");
		Element i = doc.createElement ("implements");
		i.setAttribute ("name", "java.lang.annotation.Annotation");
		e.appendChild (i);
		return e;
	}

	public static void main (String[] args)
	{
		String droiddocs = null;
		String javadocs = null;
		String annots = null;
		List<String> jar_paths = new ArrayList<String> ();
		String out_path = null;
		List<String> additional_jar_paths = new ArrayList<String> ();
		String usage = "Usage: jar2xml --jar=<jarfile> [--ref=<jarfile>] --out=<file> [--javadocpath=<javadoc>] [--droiddocpath=<droiddoc>] [--annotations=<xmlfile>]";

		for (String arg : args) {
			if (arg.startsWith ("--javadocpath=")) {
				javadocs = arg.substring (14);
			} else if (arg.startsWith ("--droiddocpath=")) {
				droiddocs = arg.substring (15);
			} else if (arg.startsWith ("--annotations=")) {
				annots = arg.substring (14);
			} else if (arg.startsWith ("--jar=")) {
				jar_paths.add (arg.substring (6));
			} else if (arg.startsWith ("--ref=")) {
				additional_jar_paths.add (arg.substring (6));
			} else if (arg.startsWith ("--out=")) {
				out_path = arg.substring (6);
			} else {
				System.err.println (usage);
				System.exit (1);
			}
		}

		if (jar_paths.size() == 0 || out_path == null) {
			System.err.println (usage);
			System.exit (1);
		}
		File dir = new File (out_path).getAbsoluteFile ().getParentFile ();
		if (!dir.exists ())
			dir.mkdirs ();

		JavaArchive jar = null;
		try {
			jar = new JavaArchive (jar_paths, additional_jar_paths);
		} catch (Exception e) {
			System.err.println ("error J2X0001: Couldn't open java archive : " + e);
			System.exit (1);
		}

		try {
			if (annots != null)
				AndroidDocScraper.loadXml (annots);
			if (droiddocs != null)
				JavaClass.addDocScraper (new DroidDocScraper (new File (droiddocs)));
			if (javadocs != null)
				JavaClass.addDocScraper (new JavaDocScraper (new File (javadocs)));
		} catch (Exception e) {
			System.err.println ("warning J2X8001: Couldn't access javadocs at specified docpath.  Continuing without it...");
		}

		Document doc = null;
		try {
			DocumentBuilderFactory builder_factory = DocumentBuilderFactory.newInstance ();
			DocumentBuilder builder = builder_factory.newDocumentBuilder ();
			doc = builder.newDocument ();
		} catch (Exception e) {
			System.err.println ("warning J2X8002: Couldn't create xml document - exception occurred:" + e.getMessage ());
		}

		try {
		Element root = doc.createElement ("api");
		doc.appendChild (root);
		for (JavaPackage pkg : jar.getPackages ())
			pkg.appendToDocument (doc, root);
		for (String ann : annotations) {
			String pkg = ann.substring (0, ann.lastIndexOf ('.'));
			NodeList nl = root.getChildNodes ();
			for (int ind = 0; ind < nl.getLength (); ind++) {
				Node n = nl.item (ind);
				if (!(n instanceof Element))
					continue;
				Element el = (Element) n;
				if (el.getAttribute ("name").equals (pkg)) {
					String local = ann.substring (pkg.length () + 1);
					el.appendChild (createAnnotationMock (doc, local.replace ("$", ".")));
				}
			}
		}
		} catch (Exception e) {
			System.err.println (e);
			System.err.println ("error J2X0002: API analyzer failed with java exception. See verbose output for details.");
			System.exit (1);
		}

		try {
			TransformerFactory transformer_factory = TransformerFactory.newInstance ();
			Transformer transformer = transformer_factory.newTransformer ();
			transformer.setOutputProperty (OutputKeys.INDENT, "yes");
			FileOutputStream stream = new FileOutputStream(out_path);
			OutputStreamWriter writer = new OutputStreamWriter(stream,"UTF-8");
			StreamResult result = new StreamResult (writer);
			DOMSource source = new DOMSource (doc);
			transformer.transform (source, result);
			writer.close ();
		} catch (Exception e) {
			System.err.println ("error J2X0003: Couldn't format xml file - exception occurred:" + e.getMessage ());
			System.exit (1);
		}
	}
}

