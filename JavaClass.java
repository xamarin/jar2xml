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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JavaClass implements Comparable<JavaClass> {

	private Class jclass;

	public JavaClass (Class jclass)
	{
		this.jclass = jclass;
	}

	public int compareTo (JavaClass jc)
	{
		return getName ().compareTo (jc.getName ());
	}

	public String getName ()
	{
		return jclass.getName ();
	}

	String[] getParameterNames (String name, Type[] types)
	{
		for (IDocScraper s : scrapers) {
			String[] names = s.getParameterNames (jclass, name, types);
			if (names != null && names.length > 0)
				return names;
		}
		return null;
	}

	void appendParameters (String name, Type[] types, Document doc, Element parent)
	{
		if (types == null || types.length == 0)
			return;

		String[] names = getParameterNames (name, types);
		
		int cnt = 0;
		for (int i = 0; i < types.length; i++) {
			Element e = doc.createElement ("parameter");
			e.setAttribute ("name", names == null ? "p" + i : names [i]);
			e.setAttribute ("type", getGenericTypeName (types [i]));
			parent.appendChild (e);
		}
	}

	void appendCtor (Constructor ctor, Document doc, Element parent)
	{
		int mods = ctor.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;
		Element e = doc.createElement ("constructor");
		e.setAttribute ("name", jclass.getSimpleName ());
		e.setAttribute ("type", jclass.getName ());
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		setDeprecatedAttr (e, ctor.getDeclaredAnnotations ());
		appendParameters (parent.getAttribute ("name"), ctor.getGenericParameterTypes (), doc, e);
		parent.appendChild (e);
	}

	void appendField (Field field, Document doc, Element parent)
	{
		int mods = field.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;

		Element e = doc.createElement ("field");
		e.setAttribute ("name", field.getName ());
		e.setAttribute ("type", getGenericTypeName (field.getGenericType ()));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("transient", Modifier.isTransient (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		e.setAttribute ("volatile", Modifier.isVolatile (mods) ? "true" : "false");
		setDeprecatedAttr (e, field.getDeclaredAnnotations ());
		if (Modifier.isStatic (mods) && Modifier.isFinal (mods) && Modifier.isPublic (mods)) {
			String type = e.getAttribute ("type");
			try {
				if (type == "int")
					e.setAttribute ("value", String.format ("%d", field.getInt (null)));
				else if (type == "byte")
					e.setAttribute ("value", String.format ("%d", field.getByte (null)));
				else if (type == "short")
					e.setAttribute ("value", String.format ("%d", field.getShort (null)));
				else if (type == "long")
					e.setAttribute ("value", String.format ("%d", field.getLong (null)));
				else if (type == "float")
					e.setAttribute ("value", String.format ("%f", field.getFloat (null)));
				else if (type == "double")
					e.setAttribute ("value", String.format ("%f", field.getDouble (null)));
				else if (type == "boolean")
					e.setAttribute ("value", field.getBoolean (null) ? "true" : "false");
				else if (type == "java.lang.String")
					e.setAttribute ("value", "\"" + ((String) field.get (null)) + "\"");
			} catch (Exception exc) {
				System.err.println ("Error accessing constant field " + field.getName () + " value for class " + getName ());
			}
		}
		parent.appendChild (e);
	}

	void appendMethod (Method method, Document doc, Element parent)
	{
		int mods = method.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;
		Element e = doc.createElement ("method");
		StringBuffer type_params = new StringBuffer ();
		for (TypeVariable tp : method.getTypeParameters ()) {
			if (type_params.length () > 0)
				type_params.append (", ");
			type_params.append (tp.getName ());
		}
		e.setAttribute ("name", method.getName () + (type_params.length () > 0 ? "<" + type_params.toString () + ">" : ""));
		e.setAttribute ("return", getGenericTypeName (method.getGenericReturnType ()));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		setDeprecatedAttr (e, method.getDeclaredAnnotations ());
		appendParameters (method.getName (), method.getGenericParameterTypes (), doc, e);
		parent.appendChild (e);
	}

	String getSignature (Method method)
	{
		StringBuffer sig = new StringBuffer ();
		sig.append (method.getName ());
		for (Type t : method.getGenericParameterTypes ()) {
			sig.append (":");
			sig.append (getGenericTypeName (t));
		}
		return sig.toString ();
	}

	public void appendToDocument (Document doc, Element parent)
	{
		int mods = jclass.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;

		Element e = doc.createElement (jclass.isInterface () ? "interface" : "class");
		if (!jclass.isInterface ()) {
			Type t = jclass.getGenericSuperclass ();
			if (t != null)
				e.setAttribute ("extends", getGenericTypeName (t));
		}

		String qualname = jclass.getName ();
		String name = qualname.substring (jclass.getPackage ().getName ().length () + 1, qualname.length ()).replace ("$", ".");
		StringBuffer type_params = new StringBuffer ();
		for (TypeVariable tp : jclass.getTypeParameters ()) {
			if (type_params.length () > 0)
				type_params.append (", ");
			type_params.append (tp.getName ());
		}
		e.setAttribute ("name", name + (type_params.length () > 0 ? "<" + type_params.toString () + ">" : ""));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		setDeprecatedAttr (e, jclass.getDeclaredAnnotations ());
		for (Type iface : jclass.getGenericInterfaces ()) {
			Element iface_elem = doc.createElement ("implements");
			iface_elem.setAttribute ("name", getGenericTypeName (iface));
			e.appendChild (iface_elem);
		}
		for (Constructor ctor : jclass.getDeclaredConstructors ())
			appendCtor (ctor, doc, e);

		Class base_class = jclass.getSuperclass ();
		Map<String, Method> methods = new HashMap <String, Method> ();
		for (Method method : jclass.getDeclaredMethods ()) {
			if (base_class != null) {
				try {
					Method base_method = base_class.getMethod (method.getName (), method.getParameterTypes ());
					if (!Modifier.isAbstract (base_method.getModifiers ()))
						continue;
				} catch (NoSuchMethodException nsme) {
				}
			}

			String key = getSignature (method);
			if (methods.containsKey (key)) {
				Type method_type = method.getGenericReturnType ();
				Method hashed = methods.get (key);
				Type hashed_type = hashed.getGenericReturnType ();
				Class mret = method_type instanceof Class ? (Class) method_type : null;
				Class hret = hashed_type instanceof Class ? (Class) hashed_type : null;
				if (mret == null || (hret != null && hret.isAssignableFrom (mret)))
					methods.put (key, method);
				else if (hret != null && !mret.isAssignableFrom (hret)) {
					System.out.println ("method collision: " + jclass.getName () + "." + key);
					System.out.println ("   " + hashed.getGenericReturnType ().toString () + " ----- " + method.getGenericReturnType ().toString ());
				}
			} else {
				methods.put (key, method);
			}
		}
		
		ArrayList <String> sigs = new ArrayList<String> (methods.keySet ());
		java.util.Collections.sort (sigs);
		for (String sig : sigs)
			appendMethod (methods.get (sig), doc, e);

		for (Field field : jclass.getDeclaredFields ())
			appendField (field, doc, e);
		parent.appendChild (e);
	}

	public static String getGenericTypeName (Type type)
	{
		if (type instanceof Class) {
			String name = ((Class) type).getName ();
			if (name.charAt (0) == '[') {
				// Array types report a jni formatted name
				String suffix = "";
				while (name.charAt (0) == '[') {
					name = name.substring (1);
					suffix = suffix + "[]";
				}
				if (name.equals ("B"))
					return "byte" + suffix;
				else if (name.equals ("C"))
					return "char" + suffix;
				else if (name.equals ("D"))
					return "double" + suffix;
				else if (name.equals ("I"))
					return "int" + suffix;
				else if (name.equals ("F"))
					return "float" + suffix;
				else if (name.equals ("J"))
					return "long" + suffix;
				else if (name.equals ("S"))
					return "short" + suffix;
				else if (name.equals ("Z"))
					return "boolean" + suffix;
				else if (name.charAt (0) == 'L')
					return name.substring (1, name.length () - 1).replace ('$', '.') + suffix;
				else {
					System.err.println ("Unexpected array type name '" + name + "'");
					return "";
				}
			}
			return name.replace ('$', '.');
		} else if (type.getClass ().toString ().equals ("class sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl")) {
			String name = type.toString ();
			int idx = name.indexOf ('<');
			if (idx > 0) {
				String tps = name.substring (idx);
				String basename = name.substring (0, idx);
				int nest = basename.lastIndexOf ('$');
				if (nest  > 0) {
					name = basename.substring (nest / 2 + 1) + tps;
					return name.replace ('$', '.');
				}
			}
			return name.replace ('$', '.');
		} else {
			return type.toString ().replace ('$', '.');
		}
	}

	void setDeprecatedAttr (Element elem, Annotation[] annotations)
	{
		boolean isDeprecated = false;
		for (Annotation a : annotations)
			if (a instanceof java.lang.Deprecated)
				isDeprecated = true;
		elem.setAttribute ("deprecated", isDeprecated ? "deprecated" : "not deprecated");
	}

	static ArrayList<IDocScraper> scrapers;

	public static void addDocScraper (IDocScraper scraper)
	{
		scrapers.add (scraper);
	}

	static {
		scrapers = new ArrayList<IDocScraper> ();
	}
}

