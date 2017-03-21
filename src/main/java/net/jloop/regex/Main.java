package net.jloop.regex;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int SPLIT = 257;
    private static final int MATCH = 256;
    private static final State matchstate = new State(MATCH);
    private static int listid;

    private static List<State> list1(State outp) {
        List<State> list = new ArrayList<>();
        list.add(outp);
        return list;
    }

    private static void patch(List<State> l, State s) {
        for (State ls : l) {
            ls.copyFrom(s);
        }
    }

    private static List<State> append(List<State> l1, List<State> l2) {
        List<State> list = new ArrayList<>();
        list.addAll(l1);
        list.addAll(l2);
        return list;
    }

    private static void push(List<Frag> stack, Frag f) {
        //stack.add(f);
        stack.add(0, f);
    }

    private static Frag pop(List<Frag> stack) {
        //return stack.remove(stack.size() - 1);
        return stack.remove(0);
    }

    private static State post2nfa(String postfix) {
        if (postfix == null) {
            return null;
        }
        List<Frag> stack = new ArrayList<>();
        for (char p : postfix.toCharArray()) {
            switch (p) {
                case ',':
                case '.': { // cat
                    Frag e2 = pop(stack);
                    Frag e1 = pop(stack);
                    patch(e1.out, e2.start);
                    push(stack, new Frag(e1.start, e2.out));
                    break;
                }
                case '|': { // alt
                    Frag e2 = pop(stack);
                    Frag e1 = pop(stack);
                    State s = new State(SPLIT, e1.start, e2.start);
                    push(stack, new Frag(s, append(e1.out, e2.out)));
                    break;
                }
                case '?': { // zero or one
                    Frag e = pop(stack);
                    State s = new State(SPLIT, e.start, new State());
                    push(stack, new Frag(s, append(e.out, list1(s.out1))));
                    break;
                }
                case '*': { // zero or more
                    Frag e = pop(stack);
                    State s = new State(SPLIT, e.start, new State());
                    patch(e.out, s);
                    push(stack, new Frag(s, list1(s.out1)));
                    break;
                }
                case '+': { // one or more
                    Frag e = pop(stack);
                    State s = new State(SPLIT, e.start, new State());
                    patch(e.out, s);
                    push(stack, new Frag(e.start, list1(s.out1)));
                    break;
                }
                default: {
                    State s = new State(p, new State(), new State());
                    push(stack, new Frag(s, list1(s.out)));
                    break;
                }
            }
        }
        Frag e = pop(stack);
        assert stack.isEmpty();
        patch(e.out, matchstate);
        return e.start;
    }

    private static List<State> startlist(State start) {
        listid++;
        List<State> l = new ArrayList<>();
        addstate(l, start);
        return l;
    }

    private static boolean ismatch(List<State> l) {
        for (State s : l) {
            if (s.c == MATCH) {
                return true;
            }
        }
        return false;
    }

    private static void addstate(List<State> l, State s) {
        if (s == null || s.c == -1 || s.lastlist == listid) {
            return;
        }
        s.lastlist = listid;
        if (s.c == SPLIT) {
            addstate(l, s.out);
            addstate(l, s.out1);
            return;
        }
        l.add(s);
    }

    /* step the nfa from the states in clist past the char c, to
     * create the next nfa state index nlist */
    private static void step(List<State> clist, int c, List<State> nlist) {
        listid++;
        //nlist.clear(); // ???
        for (State s : clist) {
            if (s.c == c) {
                addstate(nlist, s.out);
            }
        }
    }

    /* run nfa to determine whether it matches s */
    private static boolean match(State start, String s) {
        List<State> clist = startlist(start);
        List<State> nlist = new ArrayList<>();
        for (char c : s.toCharArray()) {
            step(clist, c, nlist);
            clist = nlist;
            nlist = new ArrayList<>();
        }
        return ismatch(clist);
    }

    private static void print(State s) {
        if (s.c != -1) {
            System.out.println("c " + s.c);
            if (s.out != null) print(s.out);
            if (s.out1 != null) print(s.out1);
        }
    }

    private static String re2post(String regex) {
        int natom = 0, nalt = 0;
        StringBuilder buf = new StringBuilder();
        List<Paren> pl = new ArrayList<>();
        for (char c : regex.toCharArray()) {
            switch (c) {
                case '(': {
                    if (natom > 1) {
                        --natom;
                        buf.append('.');
                    }
                    pl.add(new Paren(nalt, natom));
                    nalt = 0;
                    natom = 0;
                    break;
                }
                case '|': {
                    if (natom == 0) {
                        return null; // what?
                    }
                    while (--natom > 0) {
                        buf.append('.');
                    }
                    ++nalt;
                    break;
                }
                case ')': {
                    assert !pl.isEmpty();
                    assert natom != 0;
                    while (--natom > 0) buf.append('.');
                    for (; nalt > 0; --nalt) buf.append('|');
                    Paren p = pl.remove(pl.size() - 1);
                    nalt = p.nalt;
                    natom = p.natom;
                    ++natom;
                    break;
                }
                case '*':
                case '+':
                case '?': {
                    if (natom == 0) return null;
                    buf.append(c);
                    break;
                }
                default: {
                    if (natom > 1) {
                        --natom;
                        buf.append('.');
                    }
                    buf.append(c);
                    ++natom;
                    break;
                }
            }
        }
        assert pl.isEmpty();
        while (--natom > 0) buf.append('.');
        for (; nalt > 0; --nalt) buf.append('|');
        return buf.toString();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("two args are required");
            System.exit(1);
        }

        if (args[0] == null) {
            System.err.println("postfix regex required as arg");
            System.exit(1);
        }

        if (args[1] == null) {
            System.err.println("a string to match against is required");
            System.exit(1);
        }

        System.out.println(args[0]);
        String postfix = re2post(args[0]);
        System.out.println(postfix);
        State start = post2nfa(postfix);

        if (match(start, args[1])) {
            System.out.println("match");
            System.exit(0);
        } else {
            System.out.println("nope");
            System.exit(1);
        }
    }
}

class State {

    int c;
    State out;
    State out1;
    int lastlist;

    State() {
        c = -1;
    }

    State(int c) {
        this.c = c;
        out = new State();
        out1 = new State();
    }

    State(int c, State out, State out1) {
        this.c = c;
        this.out = out;
        this.out1 = out1;
    }

    void copyFrom(State s) {
        c = s.c;
        out = s.out;
        out1 = s.out1;
    }
}

class Frag {

    State start;
    List<State> out;

    Frag(State start, List<State> out) {
        this.start = start;
        this.out = out;
    }
}

class Paren {

    int nalt;
    int natom;

    Paren(int nalt, int natom) {
        this.nalt = nalt;
        this.natom = natom;
    }
}
