/*
 * Copyright (C) 2011 Mojavemvc.org
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
 */
package org.mojavemvc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * This annotation must be used only with singleton controllers. The singleton
 * controller instance will be created at startup if it is annotated with this
 * annotation.
 * </p>
 * 
 * <p>
 * As an example, consider the following controller:
 * </p>
 * 
 * <pre>
 * 
 * &#064;Init
 * &#064;SingletonController
 * public class StartupController {
 * 
 * }
 * </pre>
 * 
 * <p>
 * The controller above will be instantiated during initialization of the
 * org.mojavemvc.FrontController servlet.
 * </p>
 * 
 * @author Luis Antunes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Init {

}
