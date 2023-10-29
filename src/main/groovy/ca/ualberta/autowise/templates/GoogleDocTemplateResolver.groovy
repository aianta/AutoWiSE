package ca.ualberta.autowise.templates

import ca.ualberta.autowise.GoogleAPI
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.thymeleaf.IEngineConfiguration
import org.thymeleaf.templateresolver.ITemplateResolver
import org.thymeleaf.templateresolver.StringTemplateResolver
import org.thymeleaf.templateresolver.TemplateResolution
import org.thymeleaf.templateresource.ITemplateResource
import org.thymeleaf.templateresource.StringTemplateResource

import static ca.ualberta.autowise.scripts.google.DocumentSlurper.*;

class GoogleDocTemplateResolver extends StringTemplateResolver implements ITemplateResolver{
    private static final Logger log = LoggerFactory.getLogger(GoogleDocTemplateResolver.class)

    GoogleAPI googleAPI;

    GoogleDocTemplateResolver(GoogleAPI googleAPI){
        this.googleAPI = googleAPI

    }

    protected ITemplateResource computeTemplateResource(final IEngineConfiguration configuration, final String ownerTemplate, final String template, final Map<String, Object> templateResolutionAttributes) {
        log.info("Resolving email template with doc id: ${template}")
        def templateContents = syncSlurp(googleAPI, template)
        return new StringTemplateResource(templateContents);
    }

}
