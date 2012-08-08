/* 
 *  Copyright (c) 2011-2012 Xamarin Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JavaPackage implements Comparable<JavaPackage> {

	private String name;
	private ArrayList<JavaClass> classes;

	public JavaPackage (String name)
	{
		this.name = name;
		classes = new ArrayList <JavaClass> ();
	}

	public int compareTo (JavaPackage pkg)
	{
		return name.compareTo (pkg.name);
	}

	public String getName ()
	{
		return name;
	}

	public void addClass (JavaClass cls)
	{
		classes.add (cls);
	}

	public void appendToDocument (Document doc, Element parent)
	{
		Element e = doc.createElement ("package");
		e.setAttribute ("name", name);
		parent.appendChild (e);
		Collections.sort (classes);
		for (int i = 0; i < classes.size (); i++) {
			String name = classes.get (i).getName ();
			int idx = name.lastIndexOf ('.');
			String body = idx < 0 ? name : name.substring (idx + 1);
			if (isObfuscatedName (body))
				classes.get (i).setObfuscated (true);
		}
		for (JavaClass c : classes)
			c.appendToDocument (doc, e);
	}
	
	static boolean isObfuscatedName (String name)
	{
		for (char c : name.toCharArray ())
			if (c != '$' && (c < 'a' || 'z' < c))
				return false;
		return true;
	}
}

