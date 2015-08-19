/**
 * 
 */
package de.charite.compbio.hg38altlociselector.exceptions;

/**
 * Base Exception class.
 *
 * @author Marten Jäger <marten.jaeger@charite.de>
 *
 */
public class AltLociException extends AltLociSelectorException {

	private static final long serialVersionUID = 1L;

	public AltLociException() {
		super();
	}

	public AltLociException(String msg) {
		super(msg);
	}

	public AltLociException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
