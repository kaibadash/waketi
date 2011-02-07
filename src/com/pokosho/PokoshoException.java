package com.pokosho;

import java.io.IOException;

public class PokoshoException extends Exception {
	/**
	 * serialVersionUID.
	 */
	private static final long serialVersionUID = -2105145060837356952L;

	public PokoshoException() {
		super();
	}

	public PokoshoException(IOException e) {
		super(e);
	}

	public PokoshoException(String s) {
		super(s);
	}

	public PokoshoException(Throwable e) {
		super(e);
	}
}

