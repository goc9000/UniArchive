/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.util.List;

import uniarchive.models.SingletonList;

/**
 * Class for describing a change in the content of
 * an IM archive.
 */
public class IMArchiveEvent
{
	public enum Type {
		ADDING_ITEMS,
		ADDED_ITEMS,
		DELETING_ITEMS,
		DELETED_ITEMS,
		UPDATING_ITEMS,
		UPDATED_ITEMS,
		MOVING_ITEMS,
		MOVED_ITEMS,
		UPDATED_CONVERSATIONS,
		MAJOR_CHANGE
	};
	
	public Type type;
	public List<Object> items = null;
	
	/**
	 * Constructor.
	 *
	 * @param type The type of the event
	 * @param items The items involved
	 */
	public IMArchiveEvent(Type type, List<Object> items)
	{
		this.type = type;
		this.items = items;
	}
	
	/**
	 * Constructor for a single-item event.
	 *
	 * @param type The type of the event
	 * @param item The item involved
	 */
	public IMArchiveEvent(Type type, Object item)
	{
		this.type = type;
		this.items = new SingletonList<Object>(item);
	}
	
	/**
	 * Checks whether this event refers to no items.
	 * 
	 * @return True if the event is empty, false otherwise
	 */
	public boolean isEmpty()
	{
		return this.items.isEmpty();
	}
}
