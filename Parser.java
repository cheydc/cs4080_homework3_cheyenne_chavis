package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  Expr parse() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  // expression → comma ;
  private Expr expression() {
    return comma();
  }

  // comma → conditional ( "," conditional )* ;
  // Lowest precedence, left-associative.
  private Expr comma() {
    // Error production: leading comma.
    if (match(COMMA)) {
      Token op = previous();
      error(op, "Expect expression before ','.");
      // Discard RHS at correct precedence (comma operands are conditional).
      return conditional();
    }

    Expr expr = conditional();

    while (match(COMMA)) {
      Token operator = previous();
      Expr right = conditional();
      // Reuse Binary; operator is COMMA.
      // In the interpreter, evaluate left then return right.
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // conditional → equality ( "?" expression ":" conditional )? ;
  // Right-associative.
  private Expr conditional() {
    Expr expr = equality();

    if (match(QUESTION)) {
      // Allow full expression between ? and : (C behavior, includes comma).
      Expr thenBranch = expression();
      consume(COLON, "Expect ':' after then branch of conditional expression.");
      Expr elseBranch = conditional(); // right-associative
      expr = new Expr.Conditional(expr, thenBranch, elseBranch);
    }

    return expr;
  }

  // equality → comparison ( ( "!=" | "==" ) comparison )* ;
  private Expr equality() {
    // Error production: leading equality operator.
    if (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token op = previous();
      error(op, "Expect expression before '" + op.lexeme + "'.");
      // Discard RHS at correct precedence (equality operands are comparison).
      return comparison();
    }

    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
  private Expr comparison() {
    // Error production: leading comparison operator.
    if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token op = previous();
      error(op, "Expect expression before '" + op.lexeme + "'.");
      // Discard RHS at correct precedence (comparison operands are term).
      return term();
    }

    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // term → factor ( ( "-" | "+" ) factor )* ;
  private Expr term() {
    // Error production: leading '+' (leading '-' is unary and allowed).
    if (match(PLUS)) {
      Token op = previous();
      error(op, "Expect expression before '+'.");
      // Discard RHS at correct precedence (term operands are factor).
      return factor();
    }

    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // factor → unary ( ( "/" | "*" ) unary )* ;
  private Expr factor() {
    // Error production: leading '*' or '/'.
    if (match(STAR, SLASH)) {
      Token op = previous();
      error(op, "Expect expression before '" + op.lexeme + "'.");
      // Discard RHS at correct precedence (factor operands are unary).
      return unary();
    }

    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // unary → ( "!" | "-" ) unary | primary ;
  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return primary();
  }

  // primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;
  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  // ---------- helpers ----------

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  @SuppressWarnings("unused")
  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
        default:
          // Do nothing.
      }

      advance();
    }
  }
}