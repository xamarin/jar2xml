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
		for (JavaClass c : classes)
			c.appendToDocument (doc, e);
	}
}

