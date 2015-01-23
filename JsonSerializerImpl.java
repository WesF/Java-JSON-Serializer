package cs236703.s2013.hw4.solution;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cs236703.s2013.hw4.provided.JsonNotTreeException;
import cs236703.s2013.hw4.provided.JsonSerializer;
import cs236703.s2013.hw4.provided.JsonTypeException;
import cs236703.s2013.hw4.provided.StringUtils;


public class JsonSerializerImpl implements JsonSerializer {
	
	private String[] fields;
	private String[] values;
	private int length =0;
	private boolean first = true;
	private boolean firstField = true;
	private int tabSpace = 0;
	private List<String> serializedFields = new ArrayList<String>();
	//private List<String> dynamicMethods = new ArrayList<String>();
	private Map<String,List<Class<?>>> fieldMap = new HashMap<String,List<Class<?>>>();
	//private Map<String,String> methodMap = new HashMap<String,String>();

	//private
	private JsonSerializerImpl(){
		this.fields = null;
		this.values = null;
		length = 0;
	}

	//private
	private JsonSerializerImpl(String[] fieldz, String[] valuez) {
		if (fieldz.length != valuez.length)
			throw new IllegalArgumentException("field and values are not in the same length");
		length = fieldz.length;
		this.fields = new String[length];
		this.values = new String[length];
		System.arraycopy(fieldz,0,fields,0,length);
		System.arraycopy(valuez,0,values,0,length);

	}
	
    private static boolean implementsInterface(Class<?> clazz, Class<?> interfaceClass) {
        for(Class<?> interf : clazz.getInterfaces()) {
            if(interf.equals(interfaceClass)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isPrimitive(Class<?> clazz) {
        if ((Number.class).equals(clazz.getSuperclass()) ||
            clazz.equals(Boolean.class) ||
            clazz.equals(Character.class)) {
            return true;
        }
        return false;
    }

	@Override
	public String serialize(Object object, Class<?> writeAs)
					throws JsonTypeException, JsonNotTreeException,InvocationTargetException {
		StringBuilder sbu = new StringBuilder();
        if (first) {
        	first = false;
        	fieldMap.clear();
        	serializedFields.clear();
        	tabSpace = 1;
        }
        else 
        	tabSpace++;
        if (object == null)            return "\"null\"";

        String json = null;
		Class<?> clazz = object.getClass();
		
        if (implementsInterface(clazz, CharSequence.class))  //string 
            json = sbu.append("\"").append(object.toString()).append("\"").toString(); 
        else if (isPrimitive(clazz)) {
            if(clazz.equals(Character.class)) //char or Character
                json = sbu.append("\"").append(object.toString()).append("\"").toString();           
            else if(clazz.equals(Boolean.class)) 
                json = sbu.append(object.toString()).toString();     
            else /* byte, short, int, long, float, double */ 
                json = sbu.append(object.toString()).toString();
            
        } 
        else if (object instanceof Collection || object instanceof Object[]) {
            Object[] array = null;
            if (object instanceof Collection) 
                array = ((Collection<?>)object).toArray();
            else 
                array = (Object[]) object;

            json = arrayToJSON(array);
        }
        else {
        	if ( writeAs==null || Modifier.isAbstract(writeAs.getModifiers()) 
        			|| !clazz.isInstance(object)) 
        		throw new JsonTypeException();
        	firstField = true;
            sbu.append("{\n");
        	if(!writeAs.getName().equals(object.getClass().getName())){
            	sbu.append("\"").append("$Type").append("\"").append(" : ");
            	sbu.append("\"").append(writeAs.getName()).append("\"");
            	firstField = false;
            	json = sbu.append(createObject(objectToJSON(object,writeAs))).toString();
        	}
        	else
        		json = sbu.append(createObject(objectToJSON(object,writeAs))).toString();
        }            
        if (--tabSpace == 0 )
        	first = false;
        return json;
	}
	
    /**
     * Converts an array into json array.
     * @param array to convert in json array.
     * @return the json array.
     */
    private String arrayToJSON(Object[] array) 
    		throws JsonTypeException, JsonNotTreeException,InvocationTargetException {
    	
        StringBuilder sb = new StringBuilder();

        sb.append("\n[");
        boolean flag=true;
        if (array.length > 0) {
            for (Object ob : array) {
                if(!flag)
                	sb.append(",");
                else
                	flag = false;
                sb.append(serialize(ob,ob.getClass()));
            }
        }
        sb.append("]");

        return sb.toString();
    }
    
    
    /**
     * Converts an array into json array.
     * @param object to convert in json.
     * @return the json.
     */
    private String objectToJSON(Object object,Class<?> writeAs) 
			throws JsonTypeException, JsonNotTreeException,InvocationTargetException {
        StringBuilder sb = new StringBuilder();
        Class<?> clazz = writeAs;
        sb.append(addFiledsToObj());
        Object obj = object;
        while ( !clazz.getSimpleName().equals("Object") ){
        	//System.out.println("y is object of type: "+clazz.getName());
        	List<Method> methodlist = getGetMethods(clazz);
        	List<Field> fieldlist = getGetFields(clazz,methodlist);
        	for (Field field : fieldlist) {
        		checkClashes(field,methodlist);
        		boolean dynamicAnnotion =false;
        		Method method = getMethodFromList(methodlist,field);
    			if( method != null && method.getReturnType().equals(field.getType()) ){
    				Annotation[] annotationsM = method.getDeclaredAnnotations();
    				for(Annotation annotation : annotationsM){
		   				if(annotation instanceof JsonDynamic)
		   					dynamicAnnotion = true;
    				}
    				methodlist.remove(method);
    			}
    			Annotation[] annotationsF = field.getDeclaredAnnotations();
				Annotation an = null;
    		   	for(Annotation annotation : annotationsF){
    		   		if(annotation instanceof JsonField) {
    		   			an = annotation;
    	    		}
    	    	}
    		   	sb.append(fieldProccess(obj,field,an,dynamicAnnotion,method,clazz));
        	}
        	checkDynamicMethods(obj,methodlist,clazz);
        	clazz = clazz.getSuperclass();
        	obj = clazz.cast(obj);
        }
        return sb.toString();
    }
    
    private String fieldProccess(Object o,Field f,Annotation fan,boolean dan,Method m,Class<?> cls)
    					throws JsonTypeException, JsonNotTreeException,InvocationTargetException {
    	boolean tree = true;
    	List<Class<?>> list = fieldMap.get(f.getName());
    	if(list != null) {
	    	for(Class<?> c : list){
				if (f.getType().isAssignableFrom(c))
					tree = false;
	    	}
	    	if(!tree)       							throw new JsonNotTreeException();
    	}
    	StringBuilder sb = new StringBuilder();
    	f.setAccessible(true);
		if(firstField)
				firstField = false;
		else
				sb.append(",\n");
    	sb.append("\"");
    	if( fan != null)
    		sb.append(((JsonField)fan).value()).append("\"").append(" : ");
    	else
    		sb.append(f.getName()).append("\"").append(" : ");
    	try {
    		//Constructor<?> cons = cls.getConstructor();
    		//Object o = cons.newInstance();  		
	    	if(dan){
	    		serializedFields.add(f.getName());
	   			sb.append(serialize(m.invoke(o),f.getClass()));
	   			return sb.toString();
	    	}
	   		else if(!containsFieldKey(f,cls)){
	   			serializedFields.add(f.getName());
	   			sb.append(serialize(f.get(o),f.getClass()));
	   			return sb.toString();
	   		}
		}catch (IllegalAccessException e){
			throw new JsonTypeException();
		}
    	
    	return "";
    }
    
    private String checkDynamicMethods(Object o,List<Method> list,Class<?> c)
    		throws JsonTypeException, JsonNotTreeException,InvocationTargetException{
    	StringBuilder sb = new StringBuilder();
    	sb.append("");
    	try{
	    	for(Method met : list){
				Annotation[] ans = met.getDeclaredAnnotations();
				for(Annotation an : ans){
	   				if(an instanceof JsonDynamic){
	   					if( met.getReturnType().equals(Void.TYPE))
	   					    throw new InvocationTargetException(null);
	   			    	boolean tree = true;
	   					String f = toLowerCaseFirstLetter(met.getName().substring(3));
	   			    	List<Class<?>> flist = fieldMap.get(f);
	   			    	for(Class<?> cl : flist){
	   						if (met.getReturnType().isAssignableFrom(cl))
	   							tree = false;
	   			    	}
	   			    	if(!tree)       					throw new JsonNotTreeException();
	   					serializedFields.add(f);
	   					if(firstField)
	   						firstField = false;
	   					else
	   						sb.append(",\n");
	   					sb.append("\"").append(f).append("\"").append(" : ");  
	   					sb.append(serialize(met.invoke(o),c));
	   				}
				}
	    	}
    	}catch (IllegalAccessException e){
			throw new JsonTypeException();
    	}
    	return sb.toString();
    }
    
    private boolean containsFieldKey(Field f,Class<?> c){
    	boolean flag= false;
		if ( fieldMap.containsKey(f.getName()) ){
			List<Class<?>> list = fieldMap.get(f.getName());
			for(Class<?> clazz : list){
				if (c.isAssignableFrom(clazz))
					flag = true;
			}
			if( !flag ){
				list.add(c);
				fieldMap.put(f.getName(),list);
				return false;
			}
		}
		else {
			List<Class<?>> newlist = new ArrayList<Class<?>>();
			newlist.add(c);
			fieldMap.put(f.getName(),newlist);
			return false;
		}
		return true;
    }
    
    private List<Method> getGetMethods(Class<?> cls){
    	List<Method> list = new ArrayList<Method>();
    	Method[] methods = cls.getMethods();
    	for(Method m : methods){
            String name = m.getName();
            if (m.getParameterTypes().length != 0
                || name.equals("getClass")
                || name.length() <= 3
                || !name.startsWith("get") 
            	|| !(Modifier.isPublic(m.getModifiers())) ){
                continue;
            }
    		boolean ignore = false;
    		Annotation[] annotations = m.getDeclaredAnnotations();
    		for(Annotation annotation : annotations){
    			if(annotation instanceof JsonIgnore){
    				ignore = true;
    				break;
    			}
    		}
    		if(ignore)           continue;

    		list.add(m);
    	}
    	return list;
    }
    
    
    private List<Field> getGetFields(Class<?> cls,List<Method> mlist){
    	List<Field> list = new ArrayList<Field>();
    	Field[] fields = cls.getDeclaredFields();
    	for (Field f : fields) {
    		boolean ignore = false;
    		Annotation[] annotations = f.getDeclaredAnnotations();
    		for(Annotation annotation : annotations){
    			if(annotation instanceof JsonIgnore){
    				ignore = true;
    				break;
    			}
    		}
    		if(ignore)             continue;
    		
    		if( Modifier.isPublic(f.getModifiers()) )
    			list.add(f);
    		if(Modifier.isPrivate(f.getModifiers()) 
    				|| Modifier.isProtected(f.getModifiers())){
    			Method m = getMethodFromList(mlist,f);
    			if( m != null){
    				if( !m.getReturnType().equals(f.getType()))
    					continue;
    				list.add(f);
    			}	
    		}
    	}
    	return list;
    }
    
    private Method getMethodFromList(List<Method> list,Field f){
    	for(Method m : list){
    		if(m.getName().equals(StringUtils.fieldToGetterName(f.getName())))
    			return m;
    	}
    	return null;
    }
    
    private boolean checkClashes(Field f,List<Method> mlist) throws JsonTypeException{
		boolean Jfield= false;
		boolean dynamic = false;
		Annotation an= null;
    	Annotation[] annotations = f.getDeclaredAnnotations();
		for(Annotation annotation : annotations){
			if(annotation instanceof JsonField){
				Jfield = true;
				an = annotation;
				break;
			}
		}
		Annotation[] annotationsM;
    	for(Method met : mlist){
    		annotationsM= met.getDeclaredAnnotations();
			for(Annotation annotation : annotationsM){
   				if(annotation instanceof JsonDynamic)
   					dynamic = true;
			}
			if(!dynamic)       continue;
			String mf = toLowerCaseFirstLetter(met.getName().substring(3));
	    	if(serializedFields.contains(mf))
	    		throw new JsonTypeException();
	    	if(Jfield && ((JsonField)an).value().equals(mf))
	    		throw new JsonTypeException();			
    	}
    	return true;
    }
    
    
    /**
     * Puts the first letter of a String into lower case.
     * @param string the string to convert.
     * @return a String with the first letter of a String into lower case.
     */
    public static String toLowerCaseFirstLetter(String string) {
        String returnString = null;
        if (string == null || string.isEmpty()) {
            returnString = string;
        } else {
            char[] characters = string.toCharArray();
            characters[0] = Character.toLowerCase(characters[0]);
            returnString = new String(characters);
        }
        return returnString;
    }
    
    private String addFiledsToObj() {
    	int i;
    	StringBuilder sb = new StringBuilder();
    	if (fields==null && values==null)
    		return "";
    	for (i=0;i<length;i++){
			if(firstField)
				firstField = false;
			else
				sb.append(",\n");
    		sb.append(keyStringValue(fields[i],values[i]));
    	}
    	return sb.toString();
    }
    
    private String keyStringValue(String name, String value) {
        return new StringBuilder().append("\"").append(name)
                .append("\"").append(":\"").append(value).append("\"").toString();
    }
    
    private String createObject(String st) {
        StringBuilder sb = new StringBuilder();
        sb.append(st);
        sb.append("\n}");
        firstField = true;
        return sb.toString();
    }

}
