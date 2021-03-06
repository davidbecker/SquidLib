package squidpony.panel;

import squidpony.annotation.Beta;

/**
 * The abstraction of {@code SquidPanel}s, to abstract from the UI
 * implementation (i.e. whether it's awt or libgdx doesn't matter here).
 * 
 * @author smelC - Introduction of this interface, but methods were in
 *         SquidPanel already.
 * 
 * @param <T>
 *            The type of colors
 * 
 * @see ICombinedPanel The combination of two panels, one for the background,
 *      one for the foreground; a frequent use case in roguelikes.
 */
@Beta
public interface ISquidPanel<T> {

	/**
	 * Puts the character {@code c} at {@code (x, y)}.
	 * 
	 * @param x
	 * @param y
	 * @param c
	 */
	public void put(int x, int y, char c);

	/**
	 * Puts {@code color} at {@code (x, y)} (in the cell's entirety, i.e. in the
	 * background).
	 * 
	 * @param x
	 * @param y
	 * @param color
	 */
	public void put(int x, int y, T color);

	/**
	 * Puts the given string horizontally with the first character at the given
	 * offset.
	 *
	 * Does not word wrap. Characters that are not renderable (due to being at
	 * negative offsets or offsets greater than the grid size) will not be shown
	 * but will not cause any malfunctions.
	 *
	 * @param xOffset
	 *            the x coordinate of the first character
	 * @param yOffset
	 *            the y coordinate of the first character
	 * @param string
	 *            the characters to be displayed
	 * @param foreground
	 *            the color to draw the characters
	 */
	public void put(int xOffset, int yOffset, String string, T foreground);

	/**
	 * Puts the given string horizontally with the first character at the given
	 * offset, using the colors that {@code cs} provides.
	 *
	 * Does not word wrap. Characters that are not renderable (due to being at
	 * negative offsets or offsets greater than the grid size) will not be shown
	 * but will not cause any malfunctions.
	 *
	 * @param xOffset
	 *            the x coordinate of the first character
	 * @param yOffset
	 *            the y coordinate of the first character
	 * @param cs
	 *            The string to display, with its colors.
	 */
	public void put(int xOffset, int yOffset, IColoredString<? extends T> cs);

	/**
	 * Puts the character {@code c} at {@code (x, y)} with some {@code color}.
	 * 
	 * @param x
	 * @param y
	 * @param c
	 * @param color
	 */
	public void put(int x, int y, char c, T color);

	public void put(char[][] foregrounds, T[][] colors);

	/**
	 * Removes the contents of this cell, leaving a transparent space.
	 *
	 * @param x
	 * @param y
	 */
	public void clear(int x, int y);

	/**
	 * Cause everything that has been prepared for drawing (such as with put) to
	 * actually be drawn.
	 */
	public void refresh();

	public int gridWidth();

	public int gridHeight();

	/**
	 * Sets the default foreground color.
	 * 
	 * @param color
	 */
	public void setDefaultForeground(T color);

	/**
	 * @return The default foreground color (if none was set with
	 *         {@link #setDefaultForeground(Object)}), or the last color set
	 *         with {@link #setDefaultForeground(Object)}. Cannot be
	 *         {@code null}.
	 */
	public T getDefaultForegroundColor();

	/**
	 * @return The panel doing the real job, i.e. an instance of
	 *         {@code SquidPanel}. The type of colors is unspecified, as some
	 *         clients have forwarding instances of this class that hides that
	 *         the type of color of the backer differs from the type of color in
	 *         {@code this}.
	 * 
	 *         <p>
	 *         Can be {@code this} itself.
	 *         </p>
	 */
	public ISquidPanel<?> getBacker();

}
