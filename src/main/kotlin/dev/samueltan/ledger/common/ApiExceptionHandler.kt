package dev.samueltan.ledger.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException::class)
	fun handleNotFound(ex: NotFoundException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message)

	@ExceptionHandler(RuleViolationException::class)
	fun handleRuleViolation(ex: RuleViolationException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.message)
}
