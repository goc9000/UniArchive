/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

import java.util.Arrays;

/**
 * Class for storing a list of integers efficiently (i.e.
 * without the memory overhead of a Set<Integer>).
 */
public class IntList
{
	int[] _data;
	int _used;
	boolean _sorted;
	
	/**
	 * Constructor.
	 */
	public IntList()
	{
		this._data = new int[16];
		this._used = 0;
		this._sorted = false;
	}
	
	/**
	 * Adds an integer to the list.
	 * 
	 * @param integer The integer to add
	 */
	public void add(int integer)
	{
		if (this._used == this._data.length)
			this._data = Arrays.copyOf(this._data, 2*this._data.length);
		
		this._data[this._used++] = integer;
		this._sorted = false;
	}
	
	/**
	 * Checks whether an integer is present in the list.
	 * The function executes in logarithmic time if
	 * the list is sorted, and linear time otherwise.
	 * 
	 * @param integer The integer to search for
	 * @return True if the list contains the integer,
	 *         false otherwise.
	 */
	public boolean contains(int integer)
	{
		if (this._sorted) return (Arrays.binarySearch(this._data, 0, this._used, integer) >= 0);
		
		for (int i=0; i<this._used; i++) if (this._data[i] == integer) return true;
		return false;
	}
	
	/**
	 * Sorts the list. Any "contains" queries on this
	 * list will execute in logarithmic time until the
	 * list is modified.
	 */
	public void sort()
	{
		if (this._sorted) return;
		
		Arrays.sort(this._data, 0, this._used);
		this._sorted = true;
	}
	
	/**
	 * Returns an array with all the integers.
	 * 
	 * @return The set, as an array.
	 */
	public int[] toArray()
	{
		return Arrays.copyOf(this._data, this._used);
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("[");
		for (int i=0; i<this._used; i++)
		{
			if (i>0) sb.append(",");
			sb.append(this._data[i]);
		}
		sb.append("]");
		
		return sb.toString();
	}
}
