/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

/**
 * Interface for objects that wish to receive events
 * about changes in the content of an IM archive.
 */
public interface IMArchiveListener
{
	public void archiveChanged(IMArchiveEvent event);
}
