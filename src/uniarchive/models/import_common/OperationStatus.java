/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.import_common;

import java.util.ArrayList;
import java.util.List;

import uniarchive.models.import_common.Feedback;

/**
 * This class represents a generic indication from the
 * import process as to the errors and warnings produced
 * during an operation. As opposed to the case of throwing
 * an exception, the user may be offered a chance to
 * immediately address any errors or warnings and repeat
 * the offending step.
 */
public class OperationStatus extends Feedback
{
	public List<String> warnings = new ArrayList<String>();
	public List<String> errors = new ArrayList<String>();
	
	/**
	 * Constructor.
	 */
	public OperationStatus()
	{
	}
}
