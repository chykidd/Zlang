/**
 *
 * Copyright 2011-2017 Xiaofei
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package xiaofei.library.zlang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by Xiaofei on 2017/9/9.
 *
 * or_expression = and_exp || and_exp
 *
 * and_exp = comparison_exp && comparison_exp
 *
 * comparison_exp = numeric_exp > numeric_exp
 *
 * numeric_exp = term + term
 *
 * term = factor * factor
 */

class Compiler {

    private static final HashSet<Character> SPACE_CHARS = new HashSet<Character>() {
        {
            add(' ');
            add('\t');
            add('\n');
        }
    };

    private static final HashMap<String, Symbol> RESERVED_WORDS_SYMBOLS = new HashMap<String, Symbol>() {
        {
            put("END", Symbol.END);
            put("function", Symbol.FUNCTION);
            put("if", Symbol.IF);
            put("else", Symbol.ELSE);
            put("while", Symbol.WHILE);
            put("for", Symbol.FOR);
            put("to", Symbol.TO);
            put("step", Symbol.STEP);
            put("break", Symbol.BREAK);
            put("continue",Symbol.CONTINUE);
            put("return", Symbol.RETURN);
        }
    };

    private static final HashSet<Symbol> LEADING_WORDS = new HashSet<Symbol>() {
        {
            add(Symbol.IF);
            add(Symbol.WHILE);
            add(Symbol.FOR);
            add(Symbol.BREAK);
            add(Symbol.CONTINUE);
            add(Symbol.RETURN);
        }
    };

    private static final HashMap<Character, Symbol> CHARACTER_SYMBOLS = new HashMap<Character, Symbol>() {
        {
            put(',', Symbol.COMMA);
            put(';', Symbol.SEMICOLON);
            put('(', Symbol.LEFT_PARENTHESIS);
            put(')', Symbol.RIGHT_PARENTHESIS);
            put('{', Symbol.LEFT_BRACE);
            put('}', Symbol.RIGHT_BRACE);
            put('[', Symbol.LEFT_BRACKET);
            put(']', Symbol.RIGHT_BRACKET);
            put('+', Symbol.PLUS);
            put('-', Symbol.MINUS);
            put('*', Symbol.TIMES);
            put('/', Symbol.DIVIDE);
        }
    };

    private int pos = -1;

    private int lineNumber = 1;

    private int linePos = 0;

//    private int previousPos;

    private int previousLinePos;

    private char nextChar = ' '; // After read, this points to the next char to read.

    private Symbol nextSymbol; // After read, this points to the next symbol to read.

    private Object nextObject;

    private int offset;

    private int codeIndex; // The last code index

    private LabelRecorder continueRecorder = new LabelRecorder();

    private LabelRecorder breakRecorder = new LabelRecorder();

    private Map<String, Integer> symbolTable = new HashMap<>();

    private LinkedList<FunctionWrapper> neededFunctions = new LinkedList<>();

    private Library library;

    private String program;

    private ArrayList<Code> codes;

    Compiler(Library library) {
        program = library.getProgram();
        this.library = library;
    }

    private static boolean isAlpha(char ch) {
        return ch == '_' || 'a' <= ch && ch <= 'z' || 'A' <= ch && ch <= 'Z';
    }

    private static boolean isDigit(char ch) {
        return '0' <= ch && ch <= '9';
    }

    private void moveToNextChar() {
        if (++pos == program.length()) {
            throw new CompileException(CompileError.INCOMPLETE_PROGRAM,  linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "Program incomplete!");
        }
		nextChar = program.charAt(pos);
//        if (nextChar == '\r') {
//
//        }
        if (nextChar == '\n') {
            ++lineNumber;
            linePos = 0;
        } else {
            ++linePos;
        }
    }

    private void moveToNextSymbol() {
        while (SPACE_CHARS.contains(nextChar)) {
            moveToNextChar();
        }
        while (nextChar == '/' && program.charAt(pos + 1) == '*') {
            moveToNextChar();
            moveToNextChar();
            char tmp;
            do {
                tmp = nextChar;
                moveToNextChar();
            } while (tmp != '*' || nextChar != '/');
            moveToNextChar();
            while (SPACE_CHARS.contains(nextChar)) {
                moveToNextChar();
            }
        }
//        previousPos = pos;
        previousLinePos = linePos;
        if (isAlpha(nextChar)) {
            String id = "";
            do {
                id = id + nextChar;
                moveToNextChar();
            } while (isAlpha(nextChar) || isDigit(nextChar));
            if (RESERVED_WORDS_SYMBOLS.containsKey(id)) {
                nextSymbol = RESERVED_WORDS_SYMBOLS.get(id);
                nextObject = nextSymbol;
            } else if (id.equals("true") || id.equals("false")) {
                nextSymbol = Symbol.BOOLEAN;
                nextObject = id.equals("true");
            } else if (id.equals("null")) {
                nextSymbol = Symbol.NULL;
                nextObject = null;
            } else {
                nextSymbol = Symbol.ID;
                nextObject = id;
            }
        } else if (isDigit(nextChar)) {
            nextSymbol = Symbol.NUMBER;
            int intNum = nextChar - '0';
            moveToNextChar();
            while (isDigit(nextChar)) {
                intNum = intNum * 10 + nextChar - '0';
                moveToNextChar();
            }
            if (nextChar == '.') {
                double doubleNum = intNum;
                double tmp = 1;
                moveToNextChar();
                while (isDigit(nextChar)) {
                    tmp /= 10;
                    doubleNum = doubleNum + tmp * (nextChar - '0');
                    moveToNextChar();
                }
                nextObject = doubleNum;
            } else {
                nextObject = intNum;
            }
        } else if (nextChar == '\'') {
            nextSymbol = Symbol.CHARACTER;
            moveToNextChar();
            if (nextChar == '\\') {
                moveToNextChar();
            }
            nextObject = nextChar;
            moveToNextChar();
            moveToNextChar();
        } else if (nextChar == '\"') {
            nextSymbol = Symbol.STRING;
            String data = "";
            moveToNextChar();
            while (nextChar != '\"') {
                if (nextChar == '\\') {
                    moveToNextChar();
                }
                data += nextChar;
                moveToNextChar();
            }
            nextObject = data;
            moveToNextChar();
        } else if (nextChar == '<') {
            moveToNextChar();
            if (nextChar == '=') {
                nextSymbol = Symbol.LESS_EQUAL;
                moveToNextChar();
            } else {
                nextSymbol = Symbol.LESS;
            }
            nextObject = nextSymbol;
        } else if (nextChar == '>') {
            moveToNextChar();
            if (nextChar == '=') {
                nextSymbol = Symbol.GREATER_EQUAL;
                moveToNextChar();
            } else {
                nextSymbol = Symbol.GREATER;
            }
            nextObject = nextSymbol;
        } else if (nextChar == '=') {
            moveToNextChar();
            if (nextChar == '=') {
                nextSymbol = Symbol.EQUAL;
                moveToNextChar();
            } else {
                nextSymbol = Symbol.ASSIGN;
            }
            nextObject = nextSymbol;
        } else if (nextChar == '!') {
            moveToNextChar();
            if (nextChar == '=') {
                nextSymbol = Symbol.NOT_EQUAL;
                moveToNextChar();
            } else {
                nextSymbol = Symbol.NOT;
            }
            nextObject = nextSymbol;
        } else if (nextChar == '&') {
            moveToNextChar();
            if (nextChar == '&') {
                nextSymbol = Symbol.AND;
                moveToNextChar();
            } else {
                throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "&");
            }
            nextObject = nextSymbol;
        } else if (nextChar == '|') {
            moveToNextChar();
            if (nextChar == '|') {
                nextSymbol = Symbol.OR;
                moveToNextChar();
            } else {
                throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "|");
            }
            nextObject = nextSymbol;
        } else {
            nextSymbol = CHARACTER_SYMBOLS.get(nextChar);
            if (nextSymbol == null) {
                throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, Character.toString(nextChar));
            }
            nextObject = nextSymbol;
            moveToNextChar();
        }
    }

    private void generateCode(Fct fct, Object operand) {
        codes.add(new Code(fct, operand));
        ++codeIndex;
    }

    private void modifyCodeOperand(int codeIndex, Object operand) {
        codes.get(codeIndex).setOperand(operand);
    }

    private int callFunction() {
        int parameterNumber = 0;
        moveToNextSymbol();
        while (nextSymbol != Symbol.RIGHT_PARENTHESIS) {
            disjunctionExpression();
            ++parameterNumber;
            if (nextSymbol == Symbol.COMMA) {
                moveToNextSymbol();
            } else if (nextSymbol != Symbol.RIGHT_PARENTHESIS) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ") or ,");
            }
        }
        moveToNextSymbol();
        generateCode(Fct.LIT, parameterNumber);
        return parameterNumber;
    }

    private void addIntoNeededFunctions(String functionName, int parameterNumber) {
//        if (functionName.startsWith("_")) {
//            return;
//        }
        for (FunctionWrapper functionWrapper : neededFunctions) {
            if (functionWrapper.functionName.equals(functionName) && functionWrapper.parameterNumber == parameterNumber) {
                return;
            }
        }
        neededFunctions.add(new FunctionWrapper(functionName, parameterNumber));
    }

    private void factor() {
        if (nextSymbol == Symbol.ID) {
            String id = (String) nextObject;
            moveToNextSymbol();
            if (nextSymbol == Symbol.LEFT_PARENTHESIS) {
                int parameterNumber = callFunction();
                generateCode(Fct.FUN, id);// add a label to indicate we should not ignore the return value.
                addIntoNeededFunctions(id, parameterNumber);
            } else if (nextSymbol == Symbol.LEFT_BRACKET) {
                Integer address = symbolTable.get(id);
                if (address == null) {
                    throw new CompileException(CompileError.UNINITIALIZED_VARIABLE, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, id);
                }
                int dimens = 0;
                do {
                    ++dimens;
                    moveToNextSymbol();
                    numericExpression();
                    if (nextSymbol == Symbol.RIGHT_BRACKET) {
                        moveToNextSymbol();
                    } else {
                        throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "]");
                    }
                } while (nextSymbol == Symbol.LEFT_BRACKET);
                generateCode(Fct.LIT, dimens);
                generateCode(Fct.ALOD, address);
            } else {
                Integer address = symbolTable.get(id);
                if (address == null) {
                    throw new CompileException(CompileError.UNINITIALIZED_VARIABLE, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, id);
                }
                generateCode(Fct.LOD, address);
            }
        } else if (nextSymbol == Symbol.NUMBER
                || nextSymbol == Symbol.BOOLEAN
                || nextSymbol == Symbol.CHARACTER
                || nextSymbol == Symbol.STRING
                || nextSymbol == Symbol.NULL) {
            generateCode(Fct.LIT, nextObject);
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.LEFT_PARENTHESIS) {
            moveToNextSymbol();
            disjunctionExpression();
            if (nextSymbol == Symbol.RIGHT_PARENTHESIS) {
                moveToNextSymbol();
            } else {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ")");
            }
        } else if (nextSymbol == Symbol.NOT) {
            moveToNextSymbol();
            factor();
            generateCode(Fct.OPR, Opr.NOT);
        } else {
            throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "" + nextSymbol);
        }
    }

    private void term() {
        factor();
        while (nextSymbol == Symbol.TIMES || nextSymbol == Symbol.DIVIDE) {
            Symbol op = nextSymbol;
            moveToNextSymbol();
            factor();
            if (op == Symbol.TIMES) {
                generateCode(Fct.OPR, Opr.TIMES);
            } else if (op == Symbol.DIVIDE) {
                generateCode(Fct.OPR, Opr.DIVIDE);
            }
        }
    }

    private void numericExpression() {
        if (nextSymbol == Symbol.PLUS || nextSymbol == Symbol.MINUS) {
            Symbol op = nextSymbol;
            moveToNextSymbol();
            term();
            if (op == Symbol.MINUS) {
                generateCode(Fct.OPR, Opr.NEGATIVE);
            }
        } else {
            term();
        }
        while (nextSymbol == Symbol.PLUS || nextSymbol == Symbol.MINUS) {
            Symbol op =nextSymbol;
            moveToNextSymbol();
            term();
            if (op == Symbol.PLUS) {
                generateCode(Fct.OPR, Opr.PLUS);
            } else if (op == Symbol.MINUS) {
                generateCode(Fct.OPR, Opr.MINUS);
            }
        }
    }

    private void comparisonExpression() {
        numericExpression();
        if (nextSymbol == Symbol.EQUAL || nextSymbol == Symbol.NOT_EQUAL
                || nextSymbol == Symbol.LESS || nextSymbol == Symbol.LESS_EQUAL
                || nextSymbol == Symbol.GREATER || nextSymbol == Symbol.GREATER_EQUAL) {
            Symbol op = nextSymbol;
            moveToNextSymbol();
            numericExpression();
            if (op == Symbol.EQUAL) {
                generateCode(Fct.OPR, Opr.EQUAL);
            } else if (op == Symbol.NOT_EQUAL) {
                generateCode(Fct.OPR, Opr.NOT_EQUAL);
            } else if (op == Symbol.LESS) {
                generateCode(Fct.OPR, Opr.LESS);
            } else if (op == Symbol.LESS_EQUAL) {
                generateCode(Fct.OPR, Opr.LESS_EQUAL);
            } else if (op == Symbol.GREATER) {
                generateCode(Fct.OPR, Opr.GREATER);
            } else if (op == Symbol.GREATER_EQUAL) {
                generateCode(Fct.OPR, Opr.GREATER_EQUAL);
            }
        }
    }

    private void conjunctionExpression() {
        ArrayList<Integer> codeIndexes = new ArrayList<>();
        comparisonExpression();
        while (nextSymbol == Symbol.AND) {
            generateCode(Fct.JPF_SC, 0);
            codeIndexes.add(codeIndex);
            moveToNextSymbol();
            comparisonExpression();
            generateCode(Fct.OPR, Opr.AND);
        }
        for (int index : codeIndexes) {
            modifyCodeOperand(index, codeIndex + 1);
        }
    }

    private void disjunctionExpression() {
        ArrayList<Integer> codeIndexes = new ArrayList<>();
        conjunctionExpression();
        while (nextSymbol == Symbol.OR) {
            generateCode(Fct.JPT_SC, 0);
            codeIndexes.add(codeIndex);
            moveToNextSymbol();
            conjunctionExpression();
            generateCode(Fct.OPR, Opr.OR);
        }
        for (int index : codeIndexes) {
            modifyCodeOperand(index, codeIndex + 1);
        }
    }

    private void statement(boolean inLoop) {
        if (nextSymbol == Symbol.SEMICOLON) {
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.ID) {
            String id = (String) nextObject;
            moveToNextSymbol();
            if (nextSymbol == Symbol.ASSIGN) {
                Integer address = symbolTable.get(id);
                if (address == null) {
                    symbolTable.put(id, address = ++offset);
                }
                moveToNextSymbol();
                disjunctionExpression();
                generateCode(Fct.STO, address);
            } else if (nextSymbol == Symbol.LEFT_PARENTHESIS) {
                int parameterNumber = callFunction();
                generateCode(Fct.PROC, id);
                addIntoNeededFunctions(id, parameterNumber);
            } else if (nextSymbol == Symbol.LEFT_BRACKET) {
                Integer address = symbolTable.get(id);
                if (address == null) {
                    throw new CompileException(CompileError.UNINITIALIZED_ARRAY, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, id);
                }
                int dimens = 0;
                do {
                    ++dimens;
                    moveToNextSymbol();
                    numericExpression();
                    if (nextSymbol == Symbol.RIGHT_BRACKET) {
                        moveToNextSymbol();
                    } else {
                        throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "]");
                    }
                } while (nextSymbol == Symbol.LEFT_BRACKET);
                generateCode(Fct.LIT, dimens);
                if (nextSymbol != Symbol.ASSIGN) {
                    throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "=");
                }
                moveToNextSymbol();
                disjunctionExpression();
                generateCode(Fct.ASTO, address);
            } else {
                throw new CompileException(
                        CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "= or (");
            }
            if (nextSymbol != Symbol.SEMICOLON) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ";");
            }
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.IF) {
            moveToNextSymbol();
            if (nextSymbol != Symbol.LEFT_PARENTHESIS) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "(");
            }
            moveToNextSymbol();
            disjunctionExpression();
            if (nextSymbol != Symbol.RIGHT_PARENTHESIS) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ")");
            }
            moveToNextSymbol();
            generateCode(Fct.JPF, 0); // if false then jump.
            int tmp = codeIndex;
            statement(inLoop);
            modifyCodeOperand(tmp, codeIndex + 1);
            if (nextSymbol == Symbol.ELSE) {
                modifyCodeOperand(tmp, codeIndex + 2);
                generateCode(Fct.JMP, 0);
                tmp = codeIndex;
                moveToNextSymbol();
                statement(inLoop);
                modifyCodeOperand(tmp, codeIndex + 1);
            }
        } else if (nextSymbol == Symbol.LEFT_BRACE) {
            moveToNextSymbol();
            statement(inLoop);
            while (nextSymbol == Symbol.LEFT_BRACE || LEADING_WORDS.contains(nextSymbol) || nextSymbol == Symbol.ID) {
                Symbol tmp = nextSymbol;
                statement(inLoop);
                if (tmp == Symbol.LEFT_BRACE) {
                    if (nextSymbol == Symbol.RIGHT_BRACE) {
                        moveToNextSymbol();
                    } else {
                        throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "}");
                    }
                }
            }
            if (nextSymbol != Symbol.RIGHT_BRACE) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "}");
            }
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.WHILE) {
            int tmp1 = codeIndex + 1;
            moveToNextSymbol();
            if (nextSymbol != Symbol.LEFT_PARENTHESIS) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "(");
            }
            moveToNextSymbol();
            disjunctionExpression();
            if (nextSymbol != Symbol.RIGHT_PARENTHESIS) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ")");
            }
            moveToNextSymbol();
            generateCode(Fct.JPF, 0); //false then jump
            int tmp2 = codeIndex;
            breakRecorder.createNewLabel();
            continueRecorder.createNewLabel();
            statement(true);
            generateCode(Fct.JMP, tmp1);
            modifyCodeOperand(tmp2, codeIndex + 1);
            breakRecorder.modifyCode(codeIndex + 1);
            breakRecorder.deleteCurrentLabel();
            continueRecorder.modifyCode(tmp1);
            continueRecorder.deleteCurrentLabel();
        } else if (nextSymbol == Symbol.BREAK) {
            if (!inLoop) {
                throw new CompileException(CompileError.SEMANTIC_ERROR, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "'break' appears outside a loop.");
            }
            generateCode(Fct.JMP, 0);
            breakRecorder.addCode(codeIndex);
            moveToNextSymbol();
            if (nextSymbol != Symbol.SEMICOLON) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ";");
            }
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.FOR) {//for j=a to b step c
            moveToNextSymbol();
            if (nextSymbol != Symbol.ID) {
                throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "" + nextSymbol);
            }
            String id = (String) nextObject;
            Integer address = symbolTable.get(id);
            if (address == null) {
                symbolTable.put(id, address = ++offset);
            }
            moveToNextSymbol();
            if (nextSymbol != Symbol.ASSIGN) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "=");
            }
            moveToNextSymbol();
            numericExpression();
            generateCode(Fct.STO, address);
            int tmp1 = codeIndex + 1;
            if (nextSymbol != Symbol.TO) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "to");
            }
            moveToNextSymbol();
            numericExpression();
            generateCode(Fct.LOD, address);
            generateCode(Fct.OPR, Opr.GREATER_EQUAL);
            generateCode(Fct.JPF, 0);
            int tmp2 = codeIndex;
            generateCode(Fct.JMP, 0);
            int tmp3 = codeIndex;
            int tmp4 = codeIndex + 1;
            if (nextSymbol != Symbol.STEP) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "step");
            }
            moveToNextSymbol();
            numericExpression();
            generateCode(Fct.LOD, address);
            generateCode(Fct.OPR, Opr.PLUS);
            generateCode(Fct.STO, address);
            generateCode(Fct.JMP, tmp1);
            modifyCodeOperand(tmp3, codeIndex + 1);
            breakRecorder.createNewLabel();
            continueRecorder.createNewLabel();
            statement(true);
            generateCode(Fct.JMP, tmp4);
            modifyCodeOperand(tmp2, codeIndex + 1);
            breakRecorder.modifyCode(codeIndex + 1);
            breakRecorder.deleteCurrentLabel();
            continueRecorder.modifyCode(tmp4);
            continueRecorder.deleteCurrentLabel();
        } else if (nextSymbol == Symbol.CONTINUE) {
            if (!inLoop) {
                throw new CompileException(CompileError.SEMANTIC_ERROR, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "'continue' appears outside a loop.");
            }
            generateCode(Fct.JMP, 0);
            continueRecorder.addCode(codeIndex);
            moveToNextSymbol();
            if (nextSymbol != Symbol.SEMICOLON) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ";");
            }
            moveToNextSymbol();
        } else if (nextSymbol == Symbol.RETURN) {
            moveToNextSymbol();
            if (nextSymbol != Symbol.SEMICOLON) {
                disjunctionExpression();
                generateCode(Fct.FUN_RETURN, 0);
            } else {
                generateCode(Fct.VOID_RETURN, 0);
            }
            if (nextSymbol != Symbol.SEMICOLON) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ";");
            }
            moveToNextSymbol();
        }
    }

    private void function() {
		breakRecorder.init();
        continueRecorder.init();
        symbolTable.clear();
        codes = new ArrayList<>();
        codeIndex = -1;
        if (nextSymbol == null) {
            moveToNextSymbol();
        }
        if (nextSymbol != Symbol.FUNCTION) {
            throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "function");
        }
        moveToNextSymbol();
        if (nextSymbol != Symbol.ID) {
            throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "" + nextSymbol);
        }
        String functionName = (String) nextObject;
        moveToNextSymbol();
        int parameterNumber = 0;
        offset = -1;
        if (nextSymbol == Symbol.LEFT_PARENTHESIS) {
            moveToNextSymbol();
        } else {
            throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "(");
        }
        while (nextSymbol != Symbol.RIGHT_PARENTHESIS) {
            if (nextSymbol != Symbol.ID) {
                throw new CompileException(CompileError.ILLEGAL_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "" + nextSymbol);
            }
            String id = (String) nextObject;
            ++parameterNumber;
            ++offset;
            symbolTable.put(id, offset);
            moveToNextSymbol();
            if (nextSymbol != Symbol.RIGHT_PARENTHESIS && nextSymbol != Symbol.COMMA) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, ") or ,");
            }
            if (nextSymbol == Symbol.COMMA) {
                moveToNextSymbol();
            }
        }
        moveToNextSymbol();
        generateCode(Fct.INT, 0);
        int tmp = codeIndex;
        statement(false);
        generateCode(Fct.VOID_RETURN, 0);
        modifyCodeOperand(tmp, offset + 1);
        library.put(functionName, parameterNumber, codes);
    }

    void compile() {
        program += "END ";
        do {
            function();
            if (nextSymbol == Symbol.END) {
                break;
            } else if (nextSymbol != Symbol.FUNCTION) {
                throw new CompileException(CompileError.MISSING_SYMBOL, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos, "function");
            }
        } while (true);
//        library.compileDependencies();
        for (FunctionWrapper functionWrapper : neededFunctions) {
            if (!library.containsFunction(functionWrapper.functionName, functionWrapper.parameterNumber)) {
                throw new CompileException(
                        CompileError.UNDEFINED_FUNCTION, linePos == 0 ? lineNumber - 1 : lineNumber, previousLinePos,
                        "Function name: " + functionWrapper.functionName
                                + " Parameter number: " + functionWrapper.parameterNumber);
            }
        }
    }

    private static class FunctionWrapper {
        final String functionName;
        final int parameterNumber;
        FunctionWrapper(String functionName, int parameterNumber) {
            this.functionName = functionName;
            this.parameterNumber = parameterNumber;
        }
    }
    private class LabelRecorder {
        private HashMap<Integer, HashSet<Integer>> labels;
        private int currentLabel;
        void init() {
            currentLabel = 0;
            labels = new HashMap<Integer,HashSet<Integer>>();
        }

        void addCode(int codeIndex) {
            labels.get(currentLabel).add(codeIndex);
        }

        void createNewLabel() {
            labels.put(++currentLabel, new HashSet<Integer>());
        }

        void modifyCode(int target) {
            HashSet<Integer> previousCodeIndexes = labels.get(currentLabel);
            for (int index :previousCodeIndexes) {
                codes.get(index).setOperand(target);
            }
        }

        void deleteCurrentLabel() {
            labels.remove(currentLabel--);
        }
    }
}