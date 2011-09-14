/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

/**
 * Interface for objects wishing to receive information
 * on the progress of an operation.
 */
public interface ProgressListener
{
	/**
	 * Signals progress of the operation.
	 * 
	 * @param event A progress event structure
	 */
	public void onProgress(ProgressEvent progEvent);
}
