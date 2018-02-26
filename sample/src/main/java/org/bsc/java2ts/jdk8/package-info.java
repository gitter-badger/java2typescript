/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
@Java2TS(declare = { 
		@Type(java.lang.Iterable.class),
		@Type(java.lang.CharSequence.class), 
		@Type(java.lang.Comparable.class),
		@Type(java.lang.Runnable.class),
		@Type(java.lang.System.class),
		

		@Type(java.util.stream.BaseStream.class), 
		@Type(value=java.util.stream.Stream.class,export=true), 
		
		@Type(java.util.Iterator.class),
		@Type(java.util.Comparator.class), 
		@Type(java.util.Collection.class), 
		@Type(java.util.Map.class), 
		@Type(value=java.util.List.class, alias="List" ), 
		@Type(java.util.Set.class), 
		@Type(value=java.util.Arrays.class, export=true),
		@Type(java.util.HashMap.class),
		@Type(java.util.HashSet.class), 
		@Type(java.util.ArrayList.class), 
		@Type(java.util.Optional.class),

		@Type(java.nio.file.Files.class), 
		@Type(java.nio.file.Path.class), 
		@Type(java.nio.file.Paths.class),
		@Type(java.nio.file.AccessMode.class),

		@Type(java.util.function.UnaryOperator.class), 
		@Type(java.util.function.Consumer.class),
		@Type(java.util.function.Predicate.class),
		
		@Type(value=java.net.URI.class, export=true),
		@Type(java.net.URL.class),
		

		
})
package org.bsc.java2ts.jdk8;


import org.bsc.processor.annotation.Java2TS;
import org.bsc.processor.annotation.Type;