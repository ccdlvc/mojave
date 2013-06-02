/*
 * Copyright (C) 2011-2013 Mojavemvc.org
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
package org.mojavemvc.atmosphere;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atmosphere.annotation.Asynchronous;
import org.atmosphere.annotation.Broadcast;
import org.atmosphere.annotation.Cluster;
import org.atmosphere.annotation.Publish;
import org.atmosphere.annotation.Resume;
import org.atmosphere.annotation.Schedule;
import org.atmosphere.annotation.Subscribe;
import org.atmosphere.annotation.Suspend;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.BroadcastFilter;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterConfig;
import org.atmosphere.cpr.ClusterBroadcastFilter;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.di.InjectorProvider;
import org.mojavemvc.annotations.AfterAction;
import org.mojavemvc.annotations.BeforeAction;
import org.mojavemvc.aop.RequestContext;
import org.mojavemvc.initialization.AppProperties;
import org.mojavemvc.views.EmptyView;
import org.mojavemvc.views.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * @author Luis Antunes
 */
public class AtmosphereInterceptor {
    
    private static final Logger logger = 
            LoggerFactory.getLogger("org.mojavemvc.atmosphere");

    public static final String ATMOSPHERE_FRAMEWORK = 
            "mojavemvc-internal-atmosphere-framework";
    
    private static final String INSTALLATION_ERROR = 
            "The Atmosphere Framework is not installed properly " +
            "and unexpected results may occur.";
    
    private final static String SUSPENDED_RESOURCE = 
            AtmosphereInterceptor.class.getName() + ".suspendedResource";
    
    @Inject
    AppProperties appProperties;
    
    @BeforeAction
    public void beforeAction(RequestContext ctx) {
        
        AtmosphereFramework framework = (AtmosphereFramework) appProperties
                .getProperty(ATMOSPHERE_FRAMEWORK);
        HttpServletRequest req = ctx.getRequest();
        HttpServletResponse res = ctx.getResponse();
        
        try {
            
            framework.doCometSupport(
                    AtmosphereRequest.wrap(req), AtmosphereResponse.wrap(res));
            
        } catch (Exception e) {
            logger.error("error invoking Atmosphere framework", e);
        }
    }
    
    @AfterAction
    public View afterAction(RequestContext ctx) {
        
        HttpServletRequest req = ctx.getRequest();
        HttpServletResponse resp = ctx.getResponse();
        
        AtmosphereConfig config = (AtmosphereConfig) req
                .getAttribute(FrameworkConfig.ATMOSPHERE_CONFIG);
        if (config == null) {
            logger.error(INSTALLATION_ERROR);
            throw new IllegalStateException(INSTALLATION_ERROR);
        }
        
        AtmosphereResource resource = (AtmosphereResource) req
                .getAttribute(FrameworkConfig.ATMOSPHERE_RESOURCE);
        //TODO debugging
        logger.info("resource: " + resource);
        
        //TODO get current status
        //HttpServletResponse response = ctx.getResponse();
        //if (response.getStatus() == 204) {
        //    response.setStatus(200);
        //}
        
        //TODO handle Atmosphere annotations
        /*
         * This is modeled after org.atmosphere.jersey.AtmosphereFilter
         * which does its work through a com.sun.jersey.spi.container.ContainerResponseFilter
         * which means that Atmosphere annotation processing is done after
         * the resource method is processed--in fact, it must be, as 
         * org.mojavemvc.atmosphere.SuspendResponse is a possible return value 
         */
        
        View view = null;
        Object entity = ctx.getActionReturnValue();
        View marshalledEntity = ctx.getMarshalledReturnValue();
        
        if (SuspendResponse.class.isAssignableFrom(entity.getClass())) {
            suspendResponse();
            /* don't process any annotations */
            /*
             * what is the @Returns content-type on an action that
             * returns a SuspendResponse?--what will the action invoker
             * marshall this object to?
             * -- the SuspendResponse wraps an entity--the content-type
             *    applies to that entity
             * --- the action invoker will marshall the embedded entity
             */
            return view;
        }
        
        Broadcast broadcastAnnotation = ctx.getActionAnnotation(Broadcast.class);
        if (broadcastAnnotation != null) {
            
            List<ClusterBroadcastFilter> clusterBroadcastFilters = 
                    new ArrayList<ClusterBroadcastFilter>();
            
            Cluster clusterAnnotation = ctx.getActionAnnotation(Cluster.class);
            if (clusterAnnotation != null) {
                Class<? extends ClusterBroadcastFilter>[] clusterFilters = clusterAnnotation.value();
                for (Class<? extends ClusterBroadcastFilter> c : clusterFilters) {
                    try {
                        ClusterBroadcastFilter cbf = c.newInstance();
                        InjectorProvider.getInjector().inject(cbf);
                        cbf.setUri(clusterAnnotation.name());
                        clusterBroadcastFilters.add(cbf);
                    } catch (Throwable t) {
                        logger.warn("Invalid ClusterBroadcastFilter", t);
                    }
                }
            }
            
            view = broadcast(entity, req, config, clusterBroadcastFilters, 
                    broadcastAnnotation.delay(), 0, 
                    broadcastAnnotation.filters(), 
                    broadcastAnnotation.writeEntity(), null, 
                    broadcastAnnotation.resumeOnBroadcast());
        }
        
        Asynchronous asyncAnnotation = ctx.getActionAnnotation(Asynchronous.class);
        if (asyncAnnotation != null) {
            asynchronous();
        }
        
        Suspend suspendAnnotation = ctx.getActionAnnotation(Suspend.class);
        if (suspendAnnotation != null) {
            if (suspendAnnotation.resumeOnBroadcast()) {
                suspendResume();
            } else {
                suspend();
            }
        }
        
        Subscribe subscribeAnnotation = ctx.getActionAnnotation(Subscribe.class);
        if (subscribeAnnotation != null) {
            subscribe();
        }
        
        Publish publishAnnotation = ctx.getActionAnnotation(Publish.class);
        if (publishAnnotation != null) {
            view = publish(entity, req, config, publishAnnotation.value());
        }
        
        Resume resumeAnnotation = ctx.getActionAnnotation(Resume.class);
        if (resumeAnnotation != null) {
            resume();
        }
        
        Schedule scheduleAnnotation = ctx.getActionAnnotation(Schedule.class);
        if (scheduleAnnotation != null) {
            
            schedule(scheduleAnnotation.period(), 
                    scheduleAnnotation.waitFor(), 
                    entity, marshalledEntity, resource, req, resp, 
                    scheduleAnnotation.resumeOnBroadcast());
        }
        
        /*
         * TODO 
         * in org.atmosphere.jersey.AtmosphereFilter there are often 
         * calls to com.sun.jersey.spi.container.ContainerResponse.write(),
         * which writes the response's entity to the outputstream; this seems
         * to commit the response in the process, so it should be sufficient here
         * to simply return a View instead; if not, then try working with the 
         * HttpServletResponse directly instead and return an EmptyView
         * 
         * TODO if the response is properly committed above, there is no
         * need to return an EmptyView, as the action invoker will check
         * for a committed response and return an EmptyView itself
         */
        return view;
    }
    
    private View broadcast(Object entity, HttpServletRequest req, AtmosphereConfig config, 
            List<ClusterBroadcastFilter> clusterBroadcastFilters,
            long delay, int waitFor, 
            Class<? extends BroadcastFilter>[] filters, 
            boolean writeEntity, String topic, boolean resume) {
        
        View view = null;
        AtmosphereResource resource = (AtmosphereResource) req.getAttribute(SUSPENDED_RESOURCE);
        
        Broadcaster broadcaster = resource.getBroadcaster();
        Object msg = entity;
        String returnMsg = null;
        // Something went wrong if null.
        if (entity instanceof Broadcastable) {
            if (((Broadcastable) entity).getBroadcaster() != null) {
                broadcaster = ((Broadcastable) entity).getBroadcaster();
            }
            msg = ((Broadcastable) entity).getMessage();
            returnMsg = ((Broadcastable) entity).getResponseMessage().toString();
        }

        if (resume) {
            configureResumeOnBroadcast(broadcaster);
        }

        if (entity != null) {
            addFilter(broadcaster, filters, clusterBroadcastFilters);
            /*
             * TODO
             * can we return two entities, in a sense, in the
             * Broadcast? one: the return message, the other: the
             * message itself?
             * - perhaps limit the way Broadcast can be used: unlike
             *   with Jersey, we cannot return a separate message 
             *   to the requestor, different from the broadcast
             *   message?
             */
            //containerResponse.setEntity(msg);
            if (msg == null) return view;

            if (delay == -1) {
                broadcaster.broadcast(msg);
                if (entity instanceof Broadcastable) {
                    //TODO **
                    //containerResponse.setEntity(returnMsg);
                }
            } else if (delay == 0) {
                broadcaster.delayBroadcast(msg);
            } else {
                broadcaster.delayBroadcast(msg, delay, TimeUnit.SECONDS);
            }
        }
        
        if (!writeEntity) {
            view = new EmptyView();
        }
        return view;
    }
    
    private void addFilter(Broadcaster bc, Class<? extends BroadcastFilter>[] filters, 
            List<ClusterBroadcastFilter> clusterBroadcastFilters) {
        
        configureFilter(bc, filters, clusterBroadcastFilters);
    }
    
    private void configureFilter(Broadcaster bc, Class<? extends BroadcastFilter>[] filters, 
            List<ClusterBroadcastFilter> clusterBroadcastFilters) {
        
        if (bc == null) throw new RuntimeException(new IllegalStateException("Broadcaster cannot be null"));

        /**
         * Here we can't predict if it's the same set of filter shared across all Broadcaster as
         * Broadcaster can have their own BroadcasterConfig instance.
         */
        BroadcasterConfig c = bc.getBroadcasterConfig();
        // Already configured
        if (c.hasFilters()) {
            return;
        }

        if (clusterBroadcastFilters != null) {
            // Always the first one, before any transformation/filtering
            for (ClusterBroadcastFilter cbf : clusterBroadcastFilters) {
                cbf.setBroadcaster(bc);
                c.addFilter(cbf);
            }
        }

        BroadcastFilter f = null;
        if (filters != null) {
            for (Class<? extends BroadcastFilter> filter : filters) {
                try {
                    f = filter.newInstance();
                    InjectorProvider.getInjector().inject(f);
                } catch (Throwable t) {
                    logger.warn("Invalid @BroadcastFilter: " + filter, t);
                }
                c.addFilter(f);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private View publish(Object entity, HttpServletRequest req, AtmosphereConfig config, String topic) {
        
        AtmosphereResource resource = (AtmosphereResource) req.getAttribute(SUSPENDED_RESOURCE);

        Class<Broadcaster> broadCasterClass = null;
        try {
            broadCasterClass = 
                    (Class<Broadcaster>) Class.forName(
                            (String) req.getAttribute(ApplicationConfig.BROADCASTER_CLASS));
        } catch (Throwable e) {
            throw new IllegalStateException(e.getMessage());
        }
        resource.setBroadcaster(config.getBroadcasterFactory().lookup(broadCasterClass, topic, true));
        
        return broadcast(entity, req, config, new ArrayList<ClusterBroadcastFilter>(), -1, -1, null, true, topic, false);
    }
    
    private void schedule(int timeout, int waitFor, Object entity, View marshalledEntity,
            AtmosphereResource resource, HttpServletRequest req, HttpServletResponse resp, 
            boolean resume) {
        
        Object message = entity;
        Broadcaster broadcaster = resource.getBroadcaster();
        if (entity instanceof Broadcastable) {
            broadcaster = ((Broadcastable) entity).getBroadcaster();
            message = ((Broadcastable) entity).getMessage();
            entity = ((Broadcastable) entity).getResponseMessage();
        }

        if (entity != null) {
            //TODO see com.sun.jersey.spi.container.ContainerResponse.write()
            /*
             * in the case of the Mojave framework, the entity here will never be null: it
             * will always be at least a View--unless it is a Broadcastable and the 
             * getResponseMessage() returns null 
             * 
             * 3 possibilities for entity here:
             * 
             * 1. it is a View (that was marshalled to itself in the action invoker)
             * 2. it is a non-Broadcastable entity
             * 3. it was a Broadcastable and is now the Broadcastable response message
             *     
             * NOTE: committed means: write status and headers and flush the outputstream
             * 
             * if the entity is other than a SuspendResponse, as it is in this case,
             * then a @Returns annotation must be present on the method to indicate the
             * return content-type--the action invoker will marshall any embedded entity
             */
            //write entity to response outputstream
            /*
             * NOTE:
             * we can work with the marshalledEntity and we don't need to write out the 
             * entity itself, as it would have been marshalled even if it were embedded, 
             * as in the case of Broadcastable.getResponseMessage()
             */
            write(marshalledEntity, req, resp);
        }

        if (resume) {
            configureResumeOnBroadcast(broadcaster);
        }
        
        broadcaster.scheduleFixedBroadcast(message, waitFor, timeout, TimeUnit.SECONDS);
    }
    
    private void write(View marshalledEntity, HttpServletRequest req, HttpServletResponse resp) {
        
        try {
            
            marshalledEntity.render(req, resp, appProperties);
            if (!resp.isCommitted()) {
                resp.getOutputStream().flush();
            }
            
        } catch (Exception e) {
            // TODO 
            logger.error("error writing entity", e);
        }
    }
    
    private void configureResumeOnBroadcast(Broadcaster b) {
        
        Iterator<AtmosphereResource> i = b.getAtmosphereResources().iterator();
        while (i.hasNext()) {
            HttpServletRequest r = (HttpServletRequest) i.next().getRequest();
            r.setAttribute(ApplicationConfig.RESUME_ON_BROADCAST, true);
        }
    }

    private void resume() {
        // TODO Auto-generated method stub
    }

    private void subscribe() {
        // TODO Auto-generated method stub
    }

    private void suspend() {
        // TODO Auto-generated method stub
    }

    private void suspendResume() {
        // TODO Auto-generated method stub
    }

    private void asynchronous() {
        // TODO Auto-generated method stub
    }

    private void suspendResponse() {
        // TODO Auto-generated method stub
    }
}
