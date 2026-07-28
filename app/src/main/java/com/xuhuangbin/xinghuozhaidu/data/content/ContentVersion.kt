package com.xuhuangbin.xinghuozhaidu.data.content

import java.math.BigInteger

object ContentVersion {
    private val pattern = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

    fun requireValid(value: String): String {
        if (!pattern.matches(value)) {
            throw ContentPackageException("内容版本必须使用 MAJOR.MINOR.PATCH：$value")
        }
        return value
    }

    fun compare(left: String, right: String): Int {
        val leftParts = requireValid(left).split('.').map { BigInteger(it) }
        val rightParts = requireValid(right).split('.').map { BigInteger(it) }
        return leftParts.zip(rightParts)
            .firstNotNullOfOrNull { (leftPart, rightPart) ->
                leftPart.compareTo(rightPart).takeIf { it != 0 }
            }
            ?: 0
    }
}
