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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class JavaClass implements Comparable<JavaClass> {

	private Class jclass;
	private ClassNode asm;
	private Map<String,FieldNode> asmFields;
	private List<String> deprecatedFields;
	private List<String> deprecatedMethods;

	public JavaClass (Class jclass, ClassNode asm)
	{
		this.jclass = jclass;
		deprecatedFields = AndroidDocScraper.getDeprecatedFields (jclass);
		deprecatedMethods = AndroidDocScraper.getDeprecatedMethods (jclass);
		asmFields = new HashMap<String,FieldNode> ();

		for (FieldNode fn : (List<FieldNode>) asm.fields)
			asmFields.put (fn.name, fn);
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

	void appendParameters (String name, Type[] types, int typeOffset, boolean isVarArgs, Document doc, Element parent)
	{
		if (types == null || types.length == 0)
			return;

		String[] names = getParameterNames (name, types, isVarArgs);
		
		int cnt = 0;
		for (int i = typeOffset; i < types.length; i++) {
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
		setDeprecatedAttr (e, ctor.getDeclaredAnnotations (), e.getAttribute ("name"));
		
		appendParameters (parent.getAttribute ("name"), ctor.getGenericParameterTypes (), getConstructorParameterOffset (ctor), ctor.isVarArgs (), doc, e);
		e.appendChild (doc.createTextNode ("\n"));
		parent.appendChild (e);
	}
	
	int getConstructorParameterOffset (Constructor ctor)
	{
		if (Modifier.isStatic (jclass.getModifiers ()))
			return 0; // this has nothing to do with static class

		Type [] params = ctor.getGenericParameterTypes ();
		if (params.length > 0 && params [0].equals (jclass.getDeclaringClass ()))
			return 1;
		return 0;
	}

	void appendField (Field field, FieldNode asmField, Document doc, Element parent)
	{
		int mods = field.getModifiers ();
		if (!Modifier.isPublic (mods) && !Modifier.isProtected (mods))
			return;

		Element e = doc.createElement ("field");
		e.setAttribute ("name", field.getName ());
		// FIXME: at some stage we'd like to use generic name.
		//e.setAttribute ("type", getGenericTypeName (field.getGenericType ()));
		e.setAttribute ("type", getClassName (field.getType (), true));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		if (Modifier.isAbstract (mods))
			e.setAttribute ("abstract", "true");
		e.setAttribute ("transient", Modifier.isTransient (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");
		e.setAttribute ("volatile", Modifier.isVolatile (mods) ? "true" : "false");
		setDeprecatedAttr (e, field.getDeclaredAnnotations (), e.getAttribute ("name"));

		// *** constant value retrieval ***
		// sadly, there is no perfect solution:
		// - basically we want to use ASM, but sometimes ASM fails
		//   to create FieldNode instance.
		// - on the other hand, reflection 
		//   - does not allow access to protected fields.
		//   - sometimes returns "default" value for "undefined" 
		//     values such as 0 for ints and false for boolean.
		// 
		// basically we use ASM here.
		
		if (asmField == null)
			// this happens to couple of fields on java.awt.font.TextAttribute, java.lang.Double/Float and so on.
			System.err.println ("!!!!! WARNING!!! null ASM FieldNode for " + field);
		else if (asmField.value != null) {
			String type = e.getAttribute ("type");
			boolean isPublic = Modifier.isPublic (mods);
			try {
				if (type == "int")
					e.setAttribute ("value", String.format ("%d", asmField.value));
				else if (type == "byte")
					e.setAttribute ("value", String.format ("%d", asmField.value));
				else if (type == "char")
					e.setAttribute ("value", String.format ("%d", asmField.value));
				else if (type == "short")
					e.setAttribute ("value", String.format ("%d", asmField.value));
				else if (type == "long")
					e.setAttribute ("value", String.format ("%dL", asmField.value));
				else if (type == "float")
					e.setAttribute ("value", String.format ("%f", asmField.value));
				else if (type == "double") {
					// see java.lang.Double constants.
					double dvalue = (Double) asmField.value;
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
						// FIXME: here we specify "limited" digits for formatting.
						// This should fix most cases, but this could still result in not-precise value.
						// Math.E and Math.PI works with this.
						svalue = String.format ("%.15f", dvalue);
					e.setAttribute ("value", svalue);
				}
				else if (type == "boolean")
					e.setAttribute ("value", 0 == (Integer) asmField.value ? "false" : "true");
				else if (type == "java.lang.String") {
					String value = (String) asmField.value;
					if (value != null)
						e.setAttribute ("value", "\"" + value.replace ("\\", "\\\\") + "\"");
				}
				else if (Modifier.isStatic (mods) && e.getAttribute ("type").endsWith ("[]"))
					e.setAttribute ("value", "null");
			} catch (Exception exc) {
				System.err.println ("Error accessing constant field " + field.getName () + " value for class " + getName () + " : " + exc);
			}
		}
		else if (!Modifier.isStatic (mods) && e.getAttribute ("type").endsWith ("[]"))
			e.setAttribute ("value", "null");
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
		// FIXME: at some stage we'd like to use generic name.
		//Element typeParameters = getTypeParametersNode (doc, method.getTypeParameters ());
		//if (typeParameters != null)
		//	e.appendChild (typeParameters);

		e.setAttribute ("return", getGenericTypeName (method.getGenericReturnType ()));
		e.setAttribute ("final", Modifier.isFinal (mods) ? "true" : "false");
		e.setAttribute ("static", Modifier.isStatic (mods) ? "true" : "false");
		e.setAttribute ("abstract", Modifier.isAbstract (mods) ? "true" : "false");
		e.setAttribute ("native", Modifier.isNative (mods) ? "true" : "false");
		// This special condition is due to API difference between Oracle Java and android.
		if (jclass.equals (javax.net.ServerSocketFactory.class) && method.getName ().equals ("getDefault"))
			e.setAttribute ("synchronized", "true");
		else
			e.setAttribute ("synchronized", Modifier.isSynchronized (mods) ? "true" : "false");
		e.setAttribute ("visibility", Modifier.isPublic (mods) ? "public" : "protected");

		String easyName = method.getName () + "(";
		Class [] ptypes = method.getParameterTypes ();
		for (int idx = 0; idx < ptypes.length; idx++)
			easyName += (idx > 0 ? "," : "") + ptypes [idx].getSimpleName ();
		easyName += ")";
		setDeprecatedAttr (e, method.getDeclaredAnnotations (), easyName);

		appendParameters (method.getName (), method.getGenericParameterTypes (), 0, method.isVarArgs (), doc, e);

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
					return ((Class) o1).getName ().compareTo (((Class) o2).getName ());
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
		if (jclass.isArray ())
			return getClassName (jclass.getComponentType (), isFullname) + "[]";

		String qualname = jclass.getName ();
		String basename = isFullname ? qualname : qualname.substring (jclass.getPackage ().getName ().length () + 1, qualname.length ());
		return basename.replace ("$", ".");
	}

	public void appendToDocument (Document doc, Element parent)
	{
		int mods = jclass.getModifiers ();

		Element e = doc.createElement (jclass.isInterface () && !jclass.isAnnotation () ? "interface" : "class");
		if (!jclass.isInterface () || jclass.isAnnotation ()) {
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

		setDeprecatedAttr (e, jclass.getDeclaredAnnotations (), e.getAttribute ("name"));
		// FIXME: at some stage we'd like to use generic name.
		//Type [] ifaces = jclass.getGenericInterfaces ();
		Class [] ifaces = jclass.getInterfaces ();
		sortTypes (ifaces);
		for (Class iface : ifaces) {
			Element iface_elem = doc.createElement ("implements");
			// FIXME: at some stage we'd like to use generic name.
			//iface_elem.setAttribute ("name", getGenericTypeName (iface));
			iface_elem.setAttribute ("name", getClassName (iface, true));
			iface_elem.appendChild (doc.createTextNode ("\n"));
			e.appendChild (iface_elem);
		}
		for (Constructor ctor : jclass.getDeclaredConstructors ())
			appendCtor (ctor, doc, e);

		Class base_class = jclass.getSuperclass ();
		Map<String, Method> methods = new HashMap <String, Method> ();
		for (Method method : jclass.getDeclaredMethods ()) {
			int mmods = method.getModifiers ();

			if (!Modifier.isPublic (mods) && (mmods & 0x1000) != 0) {
				System.err.println ("Skipped doubtful method " + method);
				continue; // Some non-standard flag seems to detect non-declared method on the source e.g. AbstractStringBuilder.append(char)
			}

			if (!Modifier.isPublic (method.getReturnType ().getModifiers ()))
				continue;
			boolean nonPublic = false;
			Class [] ptypes = method.getParameterTypes ();
			for (int pidx = 0; pidx < ptypes.length; pidx++)
				if (!Modifier.isPublic (ptypes [pidx].getModifiers ()))
					nonPublic = true;
			if (nonPublic)
				continue;

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
					// FIXME: this causes GridView.setAdapter() skipped.
					// Removing this entire block however results in more confusion. See README.
					int base_mods = base_method.getModifiers ();
					int base_decl_class_mods = base_method.getDeclaringClass ().getModifiers (); // This is to not exclude methods that are excluded in the base type by modifiers (e.g. some AbstractStringBuilder methods)
					if (!Modifier.isAbstract (base_mods) && (Modifier.isPublic (mmods) == Modifier.isPublic (base_mods)) && Modifier.isPublic (base_decl_class_mods)) {
						if (!Modifier.isAbstract (mmods) || method.getName ().equals ("finalize")) // this is to not exclude some "override-as-abstract"  methods e.g. android.net.Uri.toString(), android.view.ViewGroup.onLayout()
							continue;
					}
				}
			}
			
			Comparator clscmp = new Comparator<Class> () {
				public int compare (Class c1, Class c2) {
					return c1.getName ().compareTo (c2.getName ());
				}
			};
			
			// These special rules are required to filter out incorrectly returned compareTo(Object) Comparable<T> implementation (maybe it is due to "erased generics").
			if (Arrays.binarySearch (jclass.getInterfaces (), Comparable.class, clscmp) >= 0 && method.getName ().equals ("compareTo") && ptypes [0].equals (Object.class)
			    // IF this worked in Java ... <code>if (... && ptypes [0] != jclass.GetGenericArguments () [0])</code>
			    && !jclass.equals (java.io.ObjectStreamField.class))
				continue;
			if (Arrays.binarySearch (jclass.getInterfaces (), Comparator.class, clscmp) >= 0 && method.getName ().equals ("compare") && ptypes.length == 2 && ptypes [0].equals (Object.class) && ptypes [1].equals (Object.class))
				continue;

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

		if (!jclass.isEnum ()) { // enums are somehow skipped.
			Field [] fields = jclass.getDeclaredFields ();
			sortFields (fields);
			for (Field field : fields)
				appendField (field, asmFields.get (field.getName ()), doc, e);
		}
		parent.appendChild (e);
	}

	void sortFields (Field [] fields)
	{
		Arrays.sort (fields, new Comparator<Field> () {
			public int compare (Field f1, Field f2)
			{
				return f1.getName ().compareTo (f2.getName ());
			}
			public boolean equals (Object obj)
			{
					return obj == this;
			}
		});
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

	void setDeprecatedAttr (Element elem, Annotation[] annotations, String name)
	{
		boolean isDeprecated = false;
		
		// by reference document (they may be excessive on old versions though)
		isDeprecated = deprecatedFields != null && deprecatedFields.indexOf (name) >= 0
			|| deprecatedMethods != null && deprecatedMethods.indexOf (name) >= 0;

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

