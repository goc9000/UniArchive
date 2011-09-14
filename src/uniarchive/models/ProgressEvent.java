/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

/**
 * Structure class for storing information regarding
 * a progress event for a running job.
 */
public class ProgressEvent
{
	public final String comment;
	public final int completedItems;
	public final int totalItems;

	/**
	 * Constructor.
	 *
	 * @param comment A description of the operation in progress
	 * @param completedItems The number of completed items
	 * @param totalItems The number of items in total
	 */
	public ProgressEvent(String comment, int completedItems, int totalItems)
	{
		this.comment = comment;
		this.completedItems = completedItems;
		this.totalItems = totalItems;
	}
}
