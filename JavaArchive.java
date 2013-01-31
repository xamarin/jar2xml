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
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class JavaArchive {

	private List<JarFile> files = new ArrayList<JarFile> ();
	private ClassLoader loader;

	public JavaArchive (List<String> filenames, List<String> additionalJars) throws Exception
	{
		List<URL> urls = new ArrayList<URL> ();
		for (String filename : filenames) {
			files.add (new JarFile (filename));
			urls.add (new File (filename).getAbsoluteFile ().toURL ());
		}
		for (String additionalJar : additionalJars)
			urls.add (new File (additionalJar).getAbsoluteFile ().toURL ());
		URL [] urlsArray = new URL [urls.size ()];
		urls.toArray (urlsArray);
		loader = new URLClassLoader (urlsArray, JavaArchive.class.getClassLoader ());
	}

	public List<JavaPackage> getPackages ()
	{
		HashMap<String, JavaPackage> packages = new HashMap <String, JavaPackage> ();
		for (JarFile file : files)
			getPackages (packages, file);

		ArrayList<JavaPackage> result = new ArrayList<JavaPackage> (packages.values ());
		Collections.sort (result);
		return result;
	}

	void getPackages (HashMap<String, JavaPackage> packages, JarFile file)
	{
		Enumeration<JarEntry> entries = file.entries ();
		while (entries.hasMoreElements ()) {
			JarEntry entry = entries.nextElement ();
			String name = entry.getName ();
			if (name.endsWith (".class")) {
				name = name.substring (0, name.length () - 6);
				try {
					InputStream stream = file.getInputStream (entry);
					ClassReader reader = new ClassReader (stream);
					ClassNode node = new ClassNode ();
					reader.accept (node, 0);
					JavaClass.asmClasses.put (node.name, node);
					
					Class c = loader.loadClass (name.replace ('/', '.'));
					//String pkgname = c.getPackage ().getName ();
					String pkgname = name.substring (0, name.lastIndexOf ('/')).replace ('/', '.');

					JavaPackage pkg = packages.get (pkgname);
					if (pkg == null) {
						pkg = new JavaPackage (pkgname);
						packages.put (pkgname, pkg);
					}
					pkg.addClass (new JavaClass (c, node));
				} catch (Throwable t) {
					t.printStackTrace ();
					System.err.println ("warning J2X9001: Couldn't load class " + name + " : " + t);
				}
			}
		}
	}
}

