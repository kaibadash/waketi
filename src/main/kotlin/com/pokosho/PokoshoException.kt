package com.pokosho

import java.io.IOException

class PokoshoException : Exception {

    constructor() : super() {}

    constructor(e: IOException) : super(e) {}

    constructor(s: String) : super(s) {}

    constructor(e: Throwable) : super(e) {}

    companion object {
        /**
         * serialVersionUID.
         */
        private val serialVersionUID = -2105145060837356952L
    }
}

