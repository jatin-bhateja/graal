/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.registersToValues;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64MathIntrinsicUnaryOp assumes that the input value is stored at the xmm0 register, and will
 * emit the output value into the xmm0 register as well.
 * {@link #emitLIRWrapper(LIRGeneratorTool, Value)} is provided for emitting necessary mov LIRs
 * before and after this LIR instruction.
 */
public abstract class AMD64MathIntrinsicUnaryOp extends AMD64LIRInstruction {

    @Def protected Value output;
    @Use protected Value input;
    @Temp protected Value[] temps;

    public AMD64MathIntrinsicUnaryOp(LIRInstructionClass<? extends AMD64MathIntrinsicUnaryOp> type, Register... registers) {
        super(type);

        input = xmm0.asValue(LIRKind.value(AMD64Kind.DOUBLE));
        output = xmm0.asValue(LIRKind.value(AMD64Kind.DOUBLE));

        temps = registersToValues(registers);
    }

    public final Variable emitLIRWrapper(LIRGeneratorTool gen, Value value) {
        LIRKind kind = LIRKind.combine(value);
        RegisterValue xmm0Value = xmm0.asValue(kind);
        gen.emitMove(xmm0Value, value);
        gen.append(this);
        Variable result = gen.newVariable(kind);
        gen.emitMove(result, xmm0Value);
        return result;
    }

}
