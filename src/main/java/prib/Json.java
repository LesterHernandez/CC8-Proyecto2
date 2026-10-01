package prib;

import java.util.*;

/** JSON mínimo de PRIB: controles entrantes planos (texto y enteros).
 * La salida admite listas y objetos para el catálogo. Rechaza claves duplicadas
 * y estructuras no previstas, en lugar de interpretar parcialmente un comando.
 */
public final class Json {
    private Json() { }
    public static String encode(Object value) {
        if (value instanceof String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> out.add(encode(key.toString()) + ":" + encode(item)));
            return out.toString();
        }
        if (value instanceof Collection<?> list) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            list.forEach(item -> out.add(encode(item))); return out.toString();
        }
        throw new IllegalArgumentException("Valor JSON no soportado");
    }

    public static Map<String, Object> parse(String text) { return new Parser(text).parse(); }
    public static String text(Map<String, Object> data, String field) {
        if (data.get(field) instanceof String value) return value;
        throw new IllegalArgumentException("Falta texto: " + field);
    }
    public static int integer(Map<String, Object> data, String field) {
        if (data.get(field) instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
            return value.intValue();
        throw new IllegalArgumentException("Falta entero válido: " + field);
    }

    private static final class Parser {
        final String input; int position;
        Parser(String input) { this.input = input; }
        void spaces() { while (position < input.length() && " \r\n\t".indexOf(input.charAt(position)) >= 0) position++; }
        boolean take(char c) {
            spaces(); if (position < input.length() && input.charAt(position) == c) { position++; return true; }
            return false;
        }
        void require(char c) { if (!take(c)) throw new IllegalArgumentException("JSON inválido"); }
        Map<String, Object> parse() {
            Map<String, Object> out = new LinkedHashMap<>(); require('{');
            if (!take('}')) {
                do {
                    String key = string(); require(':'); spaces();
                    Object value = position < input.length() && input.charAt(position) == '"' ? string() : number();
                    if (out.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Clave JSON repetida");
                    if (out.size() > 20) throw new IllegalArgumentException("Demasiados campos");
                } while (take(','));
                require('}');
            }
            spaces(); if (position != input.length()) throw new IllegalArgumentException("Sobran datos JSON");
            return out;
        }
        String string() {
            require('"'); StringBuilder out = new StringBuilder();
            while (position < input.length()) {
                char c = input.charAt(position++);
                if (c == '"') return out.toString();
                if (c < 32) throw new IllegalArgumentException("Control JSON inválido");
                if (c == '\\') {
                    if (position == input.length()) throw new IllegalArgumentException("Escape incompleto");
                    c = input.charAt(position++);
                    c = switch (c) {
                        case '"', '\\', '/' -> c;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                        case 'u' -> unicode();
                        default -> throw new IllegalArgumentException("Escape inválido");
                    };
                }
                out.append(c);
            }
            throw new IllegalArgumentException("Texto sin cerrar");
        }
        char unicode() {
            if (position + 4 > input.length()) throw new IllegalArgumentException("Unicode incompleto");
            int value = Integer.parseInt(input.substring(position, position+4), 16); position += 4; return (char)value;
        }
        long number() {
            int start = position;
            if (position < input.length() && input.charAt(position) == '-') position++;
            int digits = position;
            while (position < input.length() && input.charAt(position) >= '0' && input.charAt(position) <= '9') position++;
            if (digits == position || (position-digits > 1 && input.charAt(digits) == '0'))
                throw new IllegalArgumentException("Entero JSON inválido");
            return Long.parseLong(input.substring(start, position));
        }
    }
}
