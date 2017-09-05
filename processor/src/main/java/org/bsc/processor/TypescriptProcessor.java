package org.bsc.processor;

import static java.lang.String.format;
import static org.bsc.processor.TypescriptHelper.convertJavaToTS;
import static org.bsc.processor.TypescriptHelper.getClassDecl;
import static org.bsc.processor.TypescriptHelper.getName;
import static org.bsc.processor.TypescriptHelper.getSimpleName;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Closeable;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.FileObject;

import io.reactivex.Observable;
/**
 * 
 * @author bsoorentino
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.bsc.processor.*")
@SupportedOptions({ "ts.outfile" })
@org.kohsuke.MetaInfServices(javax.annotation.processing.Processor.class)
public class TypescriptProcessor extends AbstractProcessorEx {

    final static String ENDL = ";\n";
    
    static final List<Class<?>> PREDEFINED_CLASSES = Arrays.asList(
    		Class.class,
    		Serializable.class,
    		Closeable.class,
    		AutoCloseable.class,
    		Comparable.class,
    		Cloneable.class,
    		RandomAccess.class,
    		Consumer.class,
    		UnaryOperator.class,
    		Supplier.class,
    		Predicate.class,
    		Runnable.class
    );
    
    /**
     *
     * @param processingContext
     * @return
     */
    @Override
    public boolean process( Context processingContext ) throws Exception {
            
        final String targetFile = processingContext.getOptionMap().getOrDefault("ts.outfile", "out.d.ts");
        
        final FileObject out = super.getSourceOutputFile( Paths.get("ts"), Paths.get(targetFile));
        
        info( "output [%s]", out.getName() );
        
        final java.io.Writer w = out.openWriter();
        
        try(final java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("header.ts") ) {
        		int c;
        		while( (c = is.read()) != -1 ) w.write(c);
        }
        
	    rxEnumerateDeclaredPackageAndClass( processingContext )
            .toMap( (clazz) -> clazz.getName() )
            .flatMapObservable( (declaredClasses) -> {
            		
            		final List<Class<?>> classes = Arrays.asList(
            				declaredClasses.values().toArray( new Class[ declaredClasses.size() ]));
            		
            		// PREDEFINED CLASS
            		PREDEFINED_CLASSES
            		.forEach( (clazz) -> {
            			declaredClasses.put( clazz.getName(), clazz);
            		});
            		
                return Observable.fromIterable(classes)
                			.filter( (clazz) -> !PREDEFINED_CLASSES.contains(clazz) )
                        .map( (clazz) -> processClass(java.beans.Introspector.getBeanInfo(clazz), declaredClasses)); 
            })
            .doOnComplete( () -> w.close() )
            .subscribe( ( s ) -> w.append( s ) )  
            ;
        return true;
    }
    
    private String getPropertyDecl( Class<?> declaringClass, PropertyDescriptor pd, java.util.Map<String, Class<?>> declaredClassMap ) {

	    final StringBuilder sb = new StringBuilder();
	    
	    sb.append(pd.getName());
	    if( declaringClass.isInterface()) sb.append('?');
	
	    sb.append(':');
	    
	    final Method getter = pd.getReadMethod();
	    
	    if( getter != null ) {
	    	
			final Type rType = getter.getGenericReturnType();
			if( rType instanceof ParameterizedType ) {

				final Type pClass =  ((ParameterizedType)rType).getActualTypeArguments()[0];
			
				final String typeName = pClass.getTypeName();
			
				try {
					final String name = getName( pClass, pd.getPropertyType());
					
					final String r = rType.getTypeName()
							.replaceAll(typeName, name)
							.replaceAll("<[\\w\\?]>", "<any>")
							;

					info( "getPropertyDecl: [%s] [%s] [%s] [%s]", pd.getName(), typeName, rType.getTypeName(), r);
					
					return sb.append( r ).toString();
					
				} catch (ClassNotFoundException e) {
					
					warn( "getPropertyDecl: type [%s] not found!", typeName);
					
				}
	    			
	    		}
	    	
	    }

	    final String tsType = convertJavaToTS( pd.getPropertyType(), 
                                                declaringClass, 
                                                declaredClassMap);
       
        return sb.append(tsType)
                 .toString();
	}
    
    private String getMethodDecl( final Method m, Class<?> declaringClass, java.util.Map<String, Class<?>> declaredClassMap ) {
        final Class<?> returnType = m.getReturnType();
        
        final StringBuilder sb = new StringBuilder();
        
        if( Modifier.isStatic(m.getModifiers()) ) {

        		if( declaringClass.isInterface() ) {
        			sb.append( "// ");
        		}
        		sb.append("static ");
        	
        }
        
        sb.append(m.getName());
        //if( m.getDeclaringClass().isInterface()) sb.append('?');
        sb.append("( ");
        
        final Parameter[] params = m.getParameters();
    	
        final String params_string = 
        		Arrays.stream(params)
        				.map( (tp) -> 
        					format( "%s:%s", 
        						tp.getName(), 
        						convertJavaToTS(tp.getType(),declaringClass,declaredClassMap) ) )
        				.collect(Collectors.joining(", "))
        				;
        
	    	final String tsType = convertJavaToTS(  returnType,
	                declaringClass,
	                declaredClassMap);
	        
	    	return  sb.append(params_string)
	    				.append(" ):")
	    				.append(tsType)
	    				.toString();
        
    }
    
    private Set<Method> getMethods( final Class<?> type) {
		final Predicate<Method> include = m -> 
			!m.isBridge() && 
			!m.isSynthetic() &&
			Character.isJavaIdentifierStart(m.getName().charAt(0)) && 
			m.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);
		
		final Set<Method> methods = new LinkedHashSet<>();
		
		Stream.of(type.getMethods())
		.filter(include)
		.forEach(methods::add);
			
		Stream.of(type.getDeclaredMethods())
			.filter(include)
			.forEach(methods::add);

		return methods;

    }

    private BeanInfo getBeanInfo( final Class<?> type ) {
		try {
			return java.beans.Introspector.getBeanInfo(type);
		} catch (IntrospectionException e) {
			throw new Error(e);
		}
    }
    
    private String processNestedClasses( Class<?> type, java.util.Map<String, Class<?>> declaredClassMap ) {

        final Class<?> nestedClasses[] = type.getClasses();
        
        if( nestedClasses.length == 0 ) return "";

        final StringBuilder sb = new StringBuilder();

        sb.append( "export module " )
     	  .append(type.getSimpleName())
    	      .append(" {\n\n")
    	      ;

        Arrays.stream(nestedClasses)
        		.map( this::getBeanInfo )
        		.map( (beanInfo) -> processClass(beanInfo, declaredClassMap) )
        		.forEach( (decl) -> sb.append(decl) );
        
        return sb.append("\n} // end module ")
        				.append(type.getSimpleName())
        				.append('\n')
        				.toString()
        				;
    }
    
    private String processClass(  BeanInfo bi, java.util.Map<String, Class<?>> declaredClassMap )   {
        
        final Class<?> type = bi.getBeanDescriptor().getBeanClass();
        
        final StringBuilder sb = new StringBuilder();
        
        final PropertyDescriptor[] pds = bi.getPropertyDescriptors();
        
        final java.util.Set<String> propertySet = 
        		Arrays.stream(pds)
            .filter( TypescriptHelper::isPropertyValid )
            .map( (pd) -> getPropertyDecl( type, pd, declaredClassMap) )
            .collect( Collectors.toCollection(() -> new java.util.LinkedHashSet<String>(pds.length) ))
            ;
        
        final java.util.Set<String> methodSet =  
	        getMethods( type )
	        .stream()
	        .filter( (md) -> { // Remove setter and getter
	            return !Arrays.asList(pds).stream().anyMatch( (pd) -> {
	                    final Method rm = pd.getReadMethod();
	                    final Method wm = pd.getWriteMethod();
	                    return (md.equals(rm) || md.equals(wm));
	                });
	        })
	        .filter( (md) -> !md.getName().contains("$") ) // remove unnamed
	        .map( (md) -> getMethodDecl(md, type, declaredClassMap) )
	        .collect( Collectors.toCollection(() -> new java.util.LinkedHashSet<String>() ))
	        ;
       
        final String namespace = type.getPackage().getName();
        
        if( !type.isMemberClass() ) 
	        sb.append( "declare namespace " )
	           .append(namespace)
	           .append(" {\n\n")
	            ;
        
        sb.append( getClassDecl(type, declaredClassMap) )
          .append("\n\n");
        
        propertySet.stream().sorted().forEach((decl) ->
            sb.append( '\t' )
              .append(decl)
              .append(  ENDL ))
              ;
        methodSet.stream().sorted().forEach( (decl) ->
            sb.append( '\t' )
              .append(decl)
              .append(  ENDL ))
        		;
        
        sb.append("\n} // end ")
        		.append(getSimpleName(type))
        		.append('\n');
        
        // NESTED CLASSES
        sb.append( processNestedClasses(type, declaredClassMap) );

        if( !type.isMemberClass() ) 
        		sb.append("\n} // end namespace ")
        			.append( namespace )
        			.append('\n');
        
        return sb.toString();
           
    }
    
    
    @SuppressWarnings("unchecked")
	private Observable<? extends AnnotationValue> getAnnotationValueValue( 
    		java.util.Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry ) 
    {
    				
        final AnnotationValue av =  entry.getValue();
        return Observable.fromIterable((List<? extends AnnotationValue>)av.getValue());
    			
    }

    private Observable<Class<?>> rxEnumerateDeclaredPackageAndClass( final Context processingContext ) {
           
		final  Observable<Class<?>> result = processingContext.rxElementFromAnnotations()
            .doOnNext((e) -> info( "Anotation [%s]", e.getKind().name()) )
            .filter( (e) -> ElementKind.PACKAGE==e.getKind() || ElementKind.CLASS==e.getKind() )
            .concatMap( (e) -> Observable.fromIterable(e.getAnnotationMirrors()) ) 
            .doOnNext((m) -> info( "Mirror [%s]", m.toString() ))
            .concatMap( (am) -> Observable.fromIterable(am.getElementValues().entrySet() ))
            .filter( (entry) -> "declare".equals(String.valueOf(entry.getKey().getSimpleName())) )
            .flatMap( (entry) -> {  
            		return this.getAnnotationValueValue(entry); 
            	})
            .map( (av) -> av.getValue() )
            //.doOnNext((av) -> info( "AnnotationValue [%s]",av) )
            .ofType(DeclaredType.class)
            //.doOnNext((dt) -> info( "DeclaredType [%s]",dt) )
            .onExceptionResumeNext(Observable.empty())
            .map((DeclaredType dt) -> {
                try {
                    return Class.forName(dt.toString());
                } catch (ClassNotFoundException e1) {
                    error( "class not found [%s]",dt );
                    throw new RuntimeException(String.format("class not found [%s]",dt), e1); 
                }
            });

            return result;
    }    
    
}