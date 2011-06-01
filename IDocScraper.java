package jar2xml;

import java.lang.reflect.Type;

public interface IDocScraper {

	String[] getParameterNames (Class declarer, String name, Type[] ptypes);

}
