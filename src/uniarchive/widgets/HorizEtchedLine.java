/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

/**
 * Class for a horizontal etched line used to separate
 * dialog parts.
 */
public class HorizEtchedLine extends JPanel
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 */
	public HorizEtchedLine()
	{
		this.setBorder(new EtchedBorder(BevelBorder.LOWERED));
		this.setPreferredSize(new Dimension(100,2));
		this.setMaximumSize(new Dimension(Integer.MAX_VALUE,2));
	}
}
