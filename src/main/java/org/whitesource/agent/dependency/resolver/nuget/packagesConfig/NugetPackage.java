/**
 * Copyright (C) 2017 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent.dependency.resolver.nuget.packagesConfig;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 * @author yossi.weinberg
 */
@Root(name="package", strict=false)
public class NugetPackage implements NugetPackageInterface {

    /* --- Members --- */

    @Attribute(name="id", required=false)
    private String pkgName;
    @Attribute(name="version", required=false)
    private String pkgVersion;

    /* --- Constructors --- */

    public NugetPackage(String pkgName, String pkgVersion) {
        this.pkgName = pkgName;
        this.pkgVersion = pkgVersion;
    }

    public NugetPackage() {
    }

    /* --- Getters / Setters --- */

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public String getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(String pkgVersion) {
        this.pkgVersion = pkgVersion;
    }
}
