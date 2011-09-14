/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JPanel;

/**
 * Convenience class for a panel using the GridBag layout.
 */
public class GridBagPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	
	// Convenience variables for quick access to GridBagConstraints constants.
	public final int NONE = GridBagConstraints.NONE;
	public final int HORIZ = GridBagConstraints.HORIZONTAL;
	public final int VERT = GridBagConstraints.VERTICAL;
	public final int BOTH = GridBagConstraints.BOTH;
	
	public final int NW = GridBagConstraints.NORTHWEST;
	public final int NORTH = GridBagConstraints.NORTH;
	public final int NE = GridBagConstraints.NORTHEAST;
	public final int EAST = GridBagConstraints.EAST;
	public final int SE = GridBagConstraints.SOUTHEAST;
	public final int SOUTH = GridBagConstraints.SOUTH;
	public final int SW = GridBagConstraints.SOUTHWEST;
	public final int WEST = GridBagConstraints.WEST;
	
	public final int CENTER = GridBagConstraints.CENTER;

	/**
	 * Constructor.
	 */
	public GridBagPanel()
	{
		super(new GridBagLayout());
	}
	
	/**
	 * Convenience method for adding a component with specified grid constraints.
	 * 
	 * @param component The component to add
	 * @param gridX The horizontal grid position
	 * @param gridY The vertical grid position
	 * @param gridWidth The horizontal grid span
	 * @param gridHeight The vertical grid span
	 * @param weightX The horizontal weight
	 * @param weightY The vertical weight
	 * @param anchor A NORTH, NORTHEAST, etc. constant specifying where to place
	 *               an unstretched component in its display area
	 * @param fill A NONE, HORIZONTAL, VERTICAL or BOTH constant specifying how to
	 *             stretch a component to fill its display area
	 * @param leftPadding Left external padding
	 * @param topPadding Top external padding
	 * @param rightPadding Right external padding
	 * @param bottomPadding Bottom external padding
	 */
	public void add(Component component, int gridX, int gridY, int gridWidth, int gridHeight, double weightX,
			double weightY, int anchor, int fill, int leftPadding, int topPadding, int rightPadding, int bottomPadding)
	{
		GridBagConstraints gbc = new GridBagConstraints(gridX, gridY, gridWidth, gridHeight, weightX, weightY,
				anchor, fill, new Insets(topPadding,leftPadding,bottomPadding,rightPadding), 0, 0);
		
		this.add(component, gbc);
	}
	
	/**
	 * Convenience method for adding a component with specified grid constraints.
	 * 
	 * @param component The component to add
	 * @param gridX The horizontal grid position
	 * @param gridY The vertical grid position
	 * @param gridWidth The horizontal grid span
	 * @param gridHeight The vertical grid span
	 * @param weightX The horizontal weight
	 * @param weightY The vertical weight
	 * @param anchor A NORTH, NORTHEAST, etc. constant specifying where to place
	 *               an unstretched component in its display area
	 * @param fill A NONE, HORIZONTAL, VERTICAL or BOTH constant specifying how to
	 *             stretch a component to fill its display area
	 */
	public void add(Component component, int gridX, int gridY, int gridWidth, int gridHeight, double weightX,
			double weightY, int anchor, int fill)
	{
		this.add(component, gridX, gridY, gridWidth, gridHeight, weightX, weightY, anchor, fill, 0, 0, 0, 0);
	}
}
