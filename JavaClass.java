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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class JavaClass implements Comparable<JavaClass> {

	private Class jclass;
	private List<String> deprecatedFields;
	private List<String> deprecatedMethods;

	public JavaClass (Class jclass)
	{
		this.jclass = jclass;
		deprecatedFields = AndroidDocScraper.getDeprecatedFields (jclass);
		deprecatedMethods = AndroidDocScraper.getDeprecatedMethods (jclass);
	}

	public int compareTo (JavaClass jc)
	{
		return getName ().compareTo (jc.getName ());
	}

	public String getName ()
	{
		return jclass.getName ();
	}

	String[] getParameterNames (String name, Type[] types, boolean isVarArgs)
	{
		for (IDocScraper s : scrapers) {
			String[] names = s.getParameterNames (jclass, name, types, isVarArgs);
			if (names != null && names.length > 0)
				return names;
		}
		return null;
	}

	void appendParameters (String name, Type[] types, boolean isVarArgs, Document doc, Element parent)
	{
		if (types == null || types.length == 0)
			return;

		String[] names = getParameterNames (name, types, isVarArgs);
		
		int cnt = 0;
		for (int i = 0; i < types.length; i++) {
			Element e = doc.createElement ("parameter");
			e.setAttribute ("name", names == null ? "p" + i : names [i]);
			String type = getGenericTypeName (types [i]);
			if (isVarArgs && i == types.length - 1)
				type = type.replace ("[]", "...");
			e.setAttribute ("type", type);
			e.appendChild (doc.createTextNode ("\n"));
			parent.appendChild (e);
		}
	}
	
	String getConstructorName (Class c)
	{
		String n = "";
		Class e = c.getEnclosingClass ();
		if (e != null)
			n = getConstructorName (e);
		return (n != "" ? n + "." : n) + c.getSimpleName ();
	}

	void appendCtor (Constructor ctor, Document doc, Element parent)
	{
		int mods = ctor.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;
		Element e = doc.createElement ("constructor");
		e.setAttribute ("name", getConstructorName (jclass));
		e.setAttribute ("type", getClassName (jclass, true));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		setDeprecatedAttr (e, ctor.getDeclaredAnnotations ());
		appendParameters (parent.getAttribute ("name"), ctor.getGenericParameterTypes (), ctor.isVarArgs (), doc, e);
		e.appendChild (doc.createTextNode ("\n"));
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
		if (Modifier.isAbstract (mods))
			e.setAttribute ("abstract", "true");
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
				else if (type == "double") {
					// see java.lang.Double constants.
					double dvalue = field.getDouble (null);
					String svalue;
					
					if (dvalue == Double.MAX_VALUE)
						svalue = "1.7976931348623157E308";
					else if (dvalue == Double.MIN_VALUE)
						svalue = "4.9E-324";
					else if (Double.isNaN (dvalue))
						svalue = "(0.0 / 0.0)";
					else if (dvalue == Double.POSITIVE_INFINITY)
						svalue = "(1.0 / 0.0)";
					else if (dvalue == Double.NEGATIVE_INFINITY)
						svalue = "(-1.0 / 0.0)";
					else
						svalue = String.format ("%f", dvalue);
					e.setAttribute ("value", svalue);
				}
				else if (type == "boolean")
					e.setAttribute ("value", field.getBoolean (null) ? "true" : "false");
				else if (type == "java.lang.String")
					e.setAttribute ("value", "\"" + ((String) field.get (null)).replace ("\\", "\\\\") + "\"");
			} catch (Exception exc) {
				System.err.println ("Error accessing constant field " + field.getName () + " value for class " + getName ());
			}
		}
		e.appendChild (doc.createTextNode ("\n"));
		parent.appendChild (e);
	}

	void appendMethod (Method method, Document doc, Element parent)
	{
		int mods = method.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;
		Element e = doc.createElement ("method");
		e.setAttribute ("name", method.getName ());
		Element typeParameters = getTypeParametersNode (doc, method.getTypeParameters ());
		if (typeParameters != null)
			e.appendChild (typeParameters);

		e.setAttribute ("return", getGenericTypeName (method.getGenericReturnType ()));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("native", Modifier.isNative (mods) ? "true" : "false");
		e.setAttribute ("synchronized", Modifier.isSynchronized (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		setDeprecatedAttr (e, method.getDeclaredAnnotations ());
		appendParameters (method.getName (), method.getGenericParameterTypes (), method.isVarArgs (), doc, e);

		Class [] excTypes = method.getExceptionTypes ();
		sortClasses (excTypes);
		for (Class exc : excTypes) {
			Element exe = doc.createElement ("exception");
			exe.setAttribute ("name", getClassName (exc, false));
			exe.setAttribute ("type", getClassName (exc, true));
			exe.appendChild (doc.createTextNode ("\n"));
			e.appendChild (exe);
		}

		e.appendChild (doc.createTextNode ("\n"));
		parent.appendChild (e);
	}
	
	static void sortClasses (Class [] classes)
	{
		java.util.Arrays.sort (classes, new java.util.Comparator () {
			public int compare (Object o1, Object o2)
			{
				return ((Class) o1).getSimpleName ().compareTo (((Class) o2).getSimpleName ());
			}
			public boolean equals (Object obj)
			{
				return super.equals (obj);
			}
		});
	}
	
	static void sortTypes (Type [] types)
	{
		java.util.Arrays.sort (types, new java.util.Comparator () {
			public int compare (Object o1, Object o2)
			{
				if (o1 instanceof Class && o2 instanceof Class)
					return ((Class) o1).getSimpleName ().compareTo (((Class) o2).getSimpleName ());
				else
					return getGenericTypeName ((Type) o1).compareTo (getGenericTypeName ((Type) o2));
			}
			public boolean equals (Object obj)
			{
				return super.equals (obj);
			}
		});
	}

	static String getTypeParameters (TypeVariable<?>[] typeParameters)
	{
		if (typeParameters.length == 0)
			return "";

		StringBuffer type_params = new StringBuffer ();
		type_params.append ("<");
		for (TypeVariable tp : typeParameters) {
			if (type_params.length () > 1)
				type_params.append (", ");
			type_params.append (tp.getName ());
			Type[] bounds = tp.getBounds ();
			if (bounds.length == 1 && bounds [0] == Object.class)
				continue;
			type_params.append (" extends ").append (getGenericTypeName (bounds [0]));
			for (int i = 1; i < bounds.length; i++) {
				type_params.append (" & ").append (getGenericTypeName (bounds [i]));
			}
		}
		type_params.append (">");
		return type_params.toString ();
	}
	
	static Element getTypeParametersNode (Document doc,  TypeVariable<Method>[] tps)
	{
		if (tps.length == 0)
			return null;
		Element tps_elem = doc.createElement ("typeParameters");
		for (TypeVariable<?> tp : tps) {
			Element tp_elem = doc.createElement ("typeParameter");
			tp_elem.setAttribute ("name", tp.getName ());
			if (tp.getBounds ().length != 1 || tp.getBounds () [0].equals (Object.class)) {
				Element tcs_elem = doc.createElement ("genericConstraints");
				for (Type tc : tp.getBounds ()) {
					if (tc.equals (Object.class))
						continue;
					Element tc_elem = doc.createElement ("genericConstraint");
					Class tcc = tc instanceof Class ? (Class) tc : null;
					ParameterizedType pt = tc instanceof ParameterizedType ? (ParameterizedType) tc : null;
					if (tcc != null)
						tc_elem.setAttribute ("type", tcc.getName ());
					else if (pt != null)
						tc_elem.setAttribute ("type", pt.toString ()); // FIXME: this is not strictly compliant to the ParameterizedType API (no assured tostring() behavior to return type name)
					else
						throw new UnsupportedOperationException ("Type is " + tc.getClass ());
					tcs_elem.appendChild (tc_elem);
				}
				if (tcs_elem != null)
				tp_elem.appendChild (tcs_elem);
			}
			tps_elem.appendChild (tp_elem);
		}
		return tps_elem;
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

	static String getClassName (Class jclass, boolean isFullname)
	{
		String qualname = jclass.getName ();
		String basename = isFullname ? qualname : qualname.substring (jclass.getPackage ().getName ().length () + 1, qualname.length ());
		return basename.replace ("$", ".");
	}

	public void appendToDocument (Document doc, Element parent)
	{
		int mods = jclass.getModifiers ();

		Element e = doc.createElement (jclass.isInterface () ? "interface" : "class");
		if (!jclass.isInterface ()) {
			// FIXME: at some stage we'd like to use generic name.
			//Type t = jclass.getGenericSuperclass ();
			//if (t != null)
			//	e.setAttribute ("extends", getGenericTypeName (t));
			Class t = jclass.getSuperclass ();
			if (t != null)
				e.setAttribute ("extends", getClassName (t, true));
		}

		e.setAttribute ("name", getClassName (jclass, false));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : Modifier.isProtected (mods) ? "protected" : "");

		Element typeParameters = getTypeParametersNode (doc, jclass.getTypeParameters ());
		if (typeParameters != null)
			e.appendChild (typeParameters);

		setDeprecatedAttr (e, jclass.getDeclaredAnnotations ());
		Type [] ifaces = jclass.getGenericInterfaces ();
		sortTypes (ifaces);
		for (Type iface : ifaces) {
			Element iface_elem = doc.createElement ("implements");
			iface_elem.setAttribute ("name", getGenericTypeName (iface));
			iface_elem.appendChild (doc.createTextNode ("\n"));
			e.appendChild (iface_elem);
		}
		for (Constructor ctor : jclass.getDeclaredConstructors ())
			appendCtor (ctor, doc, e);

		Class base_class = jclass.getSuperclass ();
		Map<String, Method> methods = new HashMap <String, Method> ();
		for (Method method : jclass.getDeclaredMethods ()) {
			int mmods = method.getModifiers ();
			if (base_class != null && !Modifier.isFinal (mmods)) {
				Method base_method = null;
				Class ancestor = base_class;
				while (ancestor != null && base_method == null) {
					try {
						base_method = ancestor.getDeclaredMethod (method.getName (), method.getParameterTypes ());
					} catch (Exception ex) {
					}
					ancestor = ancestor.getSuperclass ();
				}
							
				if (base_method != null) {
					if (Modifier.isAbstract (mmods))
						continue;
					// FIXME: this causes GridView.setAdapter() skipped.
					// Removing this entire block however results in more confusion. See README.
					int base_mods = base_method.getModifiers ();
					if (!Modifier.isAbstract (base_mods) && (Modifier.isPublic (mmods) == Modifier.isPublic (base_mods)))
						continue;
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

		if (!jclass.isEnum ()) // enums are somehow skipped.
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
			String name = duplicatePackageAndClass.matcher (type.toString ()).replaceAll ("$1");
			return name.replace ('$', '.');
		} else {
			return type.toString ().replace ('$', '.');
		}
	}

	static final Pattern duplicatePackageAndClass = Pattern.compile ("([a-z0-9.]+[A-Z][a-z0-9]+)\\.\\1");

	void setDeprecatedAttr (Element elem, Annotation[] annotations)
	{
		boolean isDeprecated = false;
		
		// by reference document (they may be excessive on old versions though)
		isDeprecated = deprecatedFields != null && deprecatedFields.indexOf (elem.getAttribute ("name")) >= 0;

		// by annotations (they might not exist though)
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

