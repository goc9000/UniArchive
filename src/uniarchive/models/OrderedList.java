/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Class for a list that features some SortedSet properties:
 * elements are kept in order, and no duplicates are added.
 * The structure is optimized for sacrificing update performance
 * in favor of quick access.
 *
 * @param <T> The type of element stored in the list.
 */
public class OrderedList<T extends Comparable<T>> extends ArrayList<T> implements Set<T>
{
	private static final long serialVersionUID = 1L;

	@Override
	public void add(int index, T item)
	{
		this.add(item);
	}

	@Override
	public boolean add(T item)
	{
		int index = Collections.binarySearch(this, item);
		
		if (index >= 0) return false;
		
		super.add(-1-index, item);
		
		return true;
	}

	@Override
	public boolean contains(Object item)
	{
		return this.indexOf(item) != -1;
	}

	@SuppressWarnings("unchecked")
	@Override
	public int indexOf(Object item)
	{
		int index = Collections.binarySearch(this, (T)item);
		
		return (index >= 0) ? index : -1;
	}

	@Override
	public int lastIndexOf(Object item)
	{
		return super.indexOf(item);
	}

	@Override
	public T set(int index, T newItem)
	{
		T oldItem = this.get(index);
		
		this.remove(index);
		this.add(newItem);
		
		return oldItem;
	}
	
	
}
