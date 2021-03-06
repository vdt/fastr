package r.builtins;

import com.oracle.truffle.api.frame.*;

import r.*;
import r.data.*;
import r.errors.*;
import r.nodes.*;
import r.nodes.tools.*;
import r.nodes.truffle.*;

public class Substitute extends CallFactory {

    static final CallFactory _ = new Substitute("substitute", new String[]{"expr", "env"}, new String[] {"expr"});

    private Substitute(String name, String[] params, String[] required) {
        super(name, params, required);
    }

    public static abstract class FindVar {
        abstract Object find(RSymbol name);
    }

    public static class NeverFind extends FindVar {

        @Override
        Object find(RSymbol name) {
            return null;
        }

    }

    public static class FindInFrame extends FindVar {
        final MaterializedFrame frame;

        public FindInFrame(Frame frame) {
            this.frame = frame.materialize();
        }

        @Override
        Object find(RSymbol name) {
            return RFrameHeader.localReadNotForcing(frame, name);
        }

    }

    public static class FindInList extends FindVar {
        final RList list;
        final RArray.Names names;

        public FindInList(RList list) {
            this.list = list;
            this.names = list.names();
        }

        @Override
        Object find(RSymbol name) {
            int i = names.map(name);
            if (i < 0) {
                return null;
            } else {
                return list.getRAny(i);
            }
        }
    }

    public static class FindInEnvironment extends FindVar {
        final REnvironment env;

        public FindInEnvironment(REnvironment env) {
            this.env = env;
        }

        @Override
        Object find(RSymbol name) {
            return env.localGetNotForcing(name);
        }
    }

    @Override
    public RNode create(ASTNode call, RSymbol[] names, final RNode[] exprs) {

        ArgumentInfo ia = check(call, names, exprs);
        final int posExpr = ia.position("expr");
        final int posEnv = ia.position("env");

        RNode expr = exprs[posExpr];
        final ASTNode exprAST = expr.getAST();

        if (posEnv == -1) {
            return new BaseR(call) {

                @Override
                public Object execute(Frame frame) {
                    // symbols are not substituted from the top level
                    FindVar find = frame == null ? new NeverFind() : new FindInFrame(frame);
                    return substitute(exprAST, find);
                }
            };
        }
        return new BaseR(call) {

            @Child RNode envExpr = adoptChild(exprs[posEnv]);

            @Override
            public Object execute(Frame frame) {
                Object env = envExpr.execute(frame);
                FindVar find;
                if (env instanceof REnvironment) {
                    if (env == REnvironment.GLOBAL) {
                        find = new NeverFind();
                    } else {
                        find = new FindInEnvironment((REnvironment) env);
                    }
                } else if (env instanceof RList) {
                    find = new FindInList((RList) env);
                } else {
                    throw RError.getInvalidEnvironment(ast);
                }
                return substitute(exprAST, find);
            }
        };
    }

    private final static SubstituteVisitor substituteVisitor = new SubstituteVisitor();

    public static RAny substitute(ASTNode x, FindVar env) {
        ASTNode res = substituteVisitor.substitute(x, env);
        if (res instanceof r.nodes.Constant) {
            return ((r.nodes.Constant) res).getValue();
        } else {
            return new RLanguage(res);
        }
    }

    public static class SubstituteVisitor extends DuplicateVisitor implements Visitor {
        private FindVar env;
        private final DuplicateVisitor independentDuplicator = new DuplicateVisitor();

        public ASTNode substitute(ASTNode ast, FindVar env) {
            this.env = env;
            ast.accept(this);
            return result;
        }

        protected ASTNode substituteBinding(Object binding) {
            if (binding == null) {
                return null;
            } else if (binding instanceof RPromise) { // TODO: add handling of recursive promises when/if they're added to fastr
                RPromise p = (RPromise) binding;
                return independentDuplicator.duplicate(p.expression().getAST());
            } else if (binding instanceof RLanguage) {
                RLanguage l = (RLanguage) binding;
                return independentDuplicator.duplicate(l.get());
            } else {
                return new r.nodes.Constant((RAny) binding);
            }
        }

        @Override
        public void visit(SimpleAccessVariable n) {
            RSymbol symbol = n.getSymbol();
            Object binding = env.find(symbol);
            ASTNode newAST = substituteBinding(binding);
            if (binding == null) {
                super.visit(n); // just duplicate
            } else {
                result = newAST;
            }
        }

        @Override
        protected ArgumentList d(ArgumentList l) {
            ArgumentList newList = new ArgumentList.Default();
            for(ArgumentList.Entry e : l) {
                ASTNode n = e.getValue();
                if (n instanceof SimpleAccessVariable && ((SimpleAccessVariable) n).getSymbol() == RSymbol.THREE_DOTS_SYMBOL) {
                    Object binding = env.find(RSymbol.THREE_DOTS_SYMBOL);
                    if (binding != null && binding instanceof RDots) {
                        RDots dots = (RDots) binding;
                        RSymbol[] dnames = dots.names();
                        Object[] dvalues = dots.values();
                        int len = dnames.length;
                        for (int i = 0; i < len; i++) {
                            ASTNode dast = substituteBinding(dvalues[i]);
                            RSymbol name = dnames[i];
                            if (dast != null) {
                                newList.add(name, dast);
                            } else {
                                if (name != null) {
                                    newList.add(name, new r.nodes.SimpleAccessVariable(name));
                                } else {
                                    newList.add(name, null);
                                }
                            }
                        }
                        continue;
                    }
                }
                newList.add(e.getName(), d(e.getValue()));
            }
            return newList;
        }

        @Override
        public void visit(r.nodes.FunctionCall n) {
            RSymbol symbol = n.getName();
            Object binding = env.find(symbol);
            if (binding == null) {
                super.visit(n); // just duplicate
                return;
            }

            Object newTarget;
            if (binding instanceof RPromise) { // TODO: add handling of recursive promises when/if they're added to fastr
                RPromise p = (RPromise) binding;
                newTarget = p.expression().getAST();
                result = independentDuplicator.duplicate(p.expression().getAST());
            } else if (binding instanceof RLanguage) {
                RLanguage l = (RLanguage) binding;
                newTarget = l.get();
            } else {
                newTarget = binding;
            }

            // TODO: support e.g. substitute(list(...))
            if (newTarget instanceof SimpleAccessVariable) {
                result = new r.nodes.FunctionCall(((SimpleAccessVariable) newTarget).getSymbol(), d(n.getArgs()));
            } else if (newTarget instanceof RSymbol) {
                result = new r.nodes.FunctionCall((RSymbol) newTarget, d(n.getArgs()));
            } else {
                Utils.nyi("unsupported substitution");
                // FIXME: we cannot substitute as freely as GNU-R because we now only support a symbol as the target of the call
            }
        }
    }
}
