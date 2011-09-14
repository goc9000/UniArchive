/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

import java.util.LinkedList;
import java.util.Set;

/**
 * Convenience class for creating a list with a single
 * element.
 *
 * @param <T> The type of the element in the list
 */
public class SingletonList<T> extends LinkedList<T> implements Set<T>
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 * 
	 * @param item The single element of the list
	 */
	public SingletonList(T item)
	{
		super();
		this.add(item);
	}
}
