package cs236703.s2013.hw4.solution;

import java.lang.reflect.Constructor;

import cs236703.s2013.hw4.provided.JsonSerializer;

public class JsonSerializerFactory {
	/**
	 * Create a new JsonSerializer of type cls, using args as the arguments for the c'tor 
	 * @param cls
	 * 			the concrete type of JsonSerializer to create 
	 * @param args
	 * 			the arguments to pass to the c'tor
	 * @return
	 * 		a new JsonSerializer which was created as described
	 */
	public static JsonSerializer createSerializer(Class<? extends JsonSerializer> cls, Object ... args) {
		boolean knownSerializer = false;
		//check if the cls serializer is registered as a valid JsonSerializer
		for (Class<?> serializer : listSerializers()){
				if (cls.getName().equals(serializer.getName()))
					knownSerializer = true;
		}
		if (!knownSerializer) { //unknown JsonSerializer implementation  
				throw new IllegalArgumentException();
		}
		else {
			try {
				//set parameterTypes to search for a constructor that take this types
				Class<?>[] parameterTypes = new Class[args.length];
				for (int i = 0; i < parameterTypes.length; i++) 
						parameterTypes[i] = args[i].getClass();	
				//get the right constructor
				Constructor<? extends JsonSerializer> constructor = cls.getDeclaredConstructor(parameterTypes);
				//instantiating object using constructor object
				constructor.setAccessible(true);
				JsonSerializer serializerObject = constructor.newInstance(args);
				return serializerObject;
			}catch (Exception e){
				throw new IllegalArgumentException();
			}
		}
	}
	
	/**
	 * List all available types of JsonSerializer which are known to the factory and can be created by createSerializer 
	 * @return
	 * 		an array of the types of all the available serailizers
	 */
	public static Class<?>[] listSerializers() {
		//register all types of JsonSerializer which are known to the factory 
		// -- only one in our case --
		Class<?>[] knownSerializers = new Class<?>[1];
		knownSerializers[0] = JsonSerializerImpl.class;
		return knownSerializers;
	}
}
