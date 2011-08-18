using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Xml;
using System.Xml.XPath;

public class Scraper
{
	public static int Main (string [] args)
	{
		var doc = new XmlDocument ();
		doc.Load (args [0]);
		Console.WriteLine ("<deprecated>");
		char [] sep = new char [] {' '};
		char [] fsep = new char [] {')'};
		char [] fsep2 = new char [] {'('};
		char [] psep = new char [] {','};
		foreach (XmlElement file in doc.SelectNodes ("/deprecated/file")) {
			string output = "";
			foreach (XmlElement field in file.SelectNodes ("fields")) {
				string [] items = field.InnerText.Replace ('\n', ' ').Trim ().Split (sep, StringSplitOptions.RemoveEmptyEntries);
				if (items.Length == 0)
					continue;
				foreach (var item in items)
					output += "  <field>" + item.Replace ("<", "&lt;") + "</field>\n";
			}
			foreach (XmlElement method in file.SelectNodes ("methods")) {
				string [] items = method.InnerText.Replace ('\n', ' ').Trim ().Split (sep, StringSplitOptions.RemoveEmptyEntries);
				if (items.Length == 0)
					continue;
				// once remove whitespaces, join them.
				string all = String.Join (" ", items);
				items = all.Split (fsep, StringSplitOptions.RemoveEmptyEntries); // split at ')' to tokenize all function definitions (which should end at ')')
				foreach (var item in items) {
					// drop parameter name (i.e. keep only parameter type)
					string [] fnp = (item.Trim ()).Split (fsep2, StringSplitOptions.RemoveEmptyEntries);
					string [] signame = fnp [0].Trim ().Split (sep);
					string [] prms = fnp.Length > 1 ? fnp [1].Split (psep, StringSplitOptions.RemoveEmptyEntries) : new string [0];
					for (int i = 0; i < prms.Length; i++)
						prms [i] = prms [i].Trim ().Split (sep) [0];
					output += "  <method>" + signame [signame.Length - 1] + "(" + String.Join (",", prms).Replace ("<", "&lt;") + ")</method>\n";
				}
			}
			if (output.Length == 0)
				continue;
			string name = file.GetAttribute ("name");
			name = name.Substring (0, name.Length - 5); // .html
			Console.WriteLine ("<file name='{0}'>\n{1}</file>", name, output);
		}
		Console.WriteLine ("</deprecated>");

		return 0;
	}
}
