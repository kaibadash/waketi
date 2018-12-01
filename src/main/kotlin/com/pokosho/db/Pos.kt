package com.pokosho.db

/**
 * 品詞
 * @author kaiba
 */
enum class Pos private constructor(// enum定数から整数へ変換
    val intValue: Int
) {
    //TODO:人名とか数字とか増やさないと賢くならないだろう
    Noun(1),
    Verv(2),
    Adjective(3), /*形容詞*/
    Adverb(4), /*副詞*/
    Pronoun(5), /*代名詞*/
    Preposition(6), /*前置詞*/
    Conjunction(7), /*接続詞*/
    Interjection(8), /*感動詞*/
    Rentai(9), /*連体詞*/
    Joshi(10), /*助詞*/
    Other(255) /*記号*/

}
