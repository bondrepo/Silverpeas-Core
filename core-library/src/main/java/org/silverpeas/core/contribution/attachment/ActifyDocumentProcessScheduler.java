/*
 * Copyright (C) 2000-2014 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Writer Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have recieved a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/legal/licensing"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.silverpeas.core.contribution.attachment;

import org.silverpeas.core.scheduler.Job;
import org.silverpeas.core.scheduler.JobExecutionContext;
import org.silverpeas.core.scheduler.Scheduler;
import org.silverpeas.core.scheduler.SchedulerEvent;
import org.silverpeas.core.scheduler.SchedulerEventListener;
import org.silverpeas.core.scheduler.SchedulerException;
import org.silverpeas.core.scheduler.SchedulerProvider;
import org.silverpeas.core.scheduler.trigger.JobTrigger;
import org.silverpeas.core.initialization.Initialization;
import org.silverpeas.core.util.file.FileUtil;
import org.silverpeas.core.ForeignPK;
import java.io.File;
import java.text.ParseException;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.silverpeas.core.contribution.attachment.model.DocumentType;
import org.silverpeas.core.contribution.attachment.model.SimpleAttachment;
import org.silverpeas.core.contribution.attachment.model.SimpleDocument;
import org.silverpeas.core.contribution.attachment.model.SimpleDocumentPK;
import org.silverpeas.core.contribution.attachment.model.UnlockContext;
import org.silverpeas.core.contribution.attachment.model.UnlockOption;
import org.silverpeas.core.contribution.attachment.util.SimpleDocumentList;
import org.silverpeas.core.util.logging.SilverLogger;

/**
 * A scheduler of processing of the files generated by Actify from CAD documents.
 *
 * It schedules two distincts tasks: one for importing the 3D documents generated by Actify into
 * Silverpeas and another one for purging the 3D documents generated by Actify once they were
 * imported into Silverpeas.
 *
 * @author mmoquillon
 */
public class ActifyDocumentProcessScheduler implements SchedulerEventListener, Initialization {

  private final SilverLogger logger = SilverLogger.getLogger(this);

  @Override
  public void init() {
    if (ActifyDocumentProcessor.isActifySupportEnabled()) {
      try {
        String cronScheduleProcess = ActifyDocumentProcessor.getCRONForActifyImport();
        String cronSchedulePurge = ActifyDocumentProcessor.getCRONForActifyPurge();

        Scheduler scheduler = SchedulerProvider.getScheduler();

        Job actifyDocumentImporter = getActifyDocumentImporter();
        Job actifyDocumentCleaner = getActifyDocumentCleaner();

        scheduler.unscheduleJob(actifyDocumentImporter.getName());
        scheduler.unscheduleJob(actifyDocumentCleaner.getName());

        JobTrigger processTrigger = JobTrigger.triggerAt(cronScheduleProcess);
        scheduler.scheduleJob(actifyDocumentImporter, processTrigger, this);

        JobTrigger purgeTrigger = JobTrigger.triggerAt(cronSchedulePurge);
        scheduler.scheduleJob(actifyDocumentCleaner, purgeTrigger, this);
      } catch (SchedulerException|ParseException e) {
        logger.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public void triggerFired(SchedulerEvent anEvent) throws Exception {
    logger.info("Starting of the job ''{0}''", anEvent.getJobExecutionContext().
        getJobName());
  }

  @Override
  public void jobSucceeded(SchedulerEvent anEvent) {
    logger.info("The job ''{0}'' has succeeded", anEvent.getJobExecutionContext().
        getJobName());
  }

  @Override
  public void jobFailed(SchedulerEvent anEvent) {
    String errorMsg = "";
    if (anEvent.isExceptionThrown()) {
      errorMsg = ": " + anEvent.getJobThrowable().getMessage();
    }
    logger.error("The job ''{0}'' has failed {1}",
        new String[]{anEvent.getJobExecutionContext().getJobName(), errorMsg});
  }

  protected Job getActifyDocumentImporter() {
    return new Job("ImportActify") {

      @Override
      public void execute(JobExecutionContext context) throws Exception {
        logger.info("Importation of the 3D documents generated by Actify...");
        String publicationId;
        String componentId;

        long now = new Date().getTime();
        AttachmentService attachmentService = AttachmentServiceProvider.getAttachmentService();

        String resultActifyPath = ActifyDocumentProcessor.getActifyResultPath();
        int delayBeforeProcess = ActifyDocumentProcessor.getDelayBeforeImport();

        File folderToAnalyse = new File(resultActifyPath);
        File[] elementsList = folderToAnalyse.listFiles();

        // List all folders in Actify
        for (File element : elementsList) {
          long lastModified = element.lastModified();
          String dirName = element.getName();
          String resultActifyFullPath = resultActifyPath + File.separator + dirName;

          // Directory to process?
          if (isManagedBySilverpeas(element)
              && (lastModified + delayBeforeProcess * 1000 * 60 < now)) {
            int separatorIdx = dirName.lastIndexOf('_');
            boolean isVersioned = dirName.startsWith("v_");
            componentId = dirName.
                substring(2, separatorIdx);
            publicationId = dirName.substring(separatorIdx + 1);

            String detailPathToAnalyse = element.getAbsolutePath();
            folderToAnalyse = new File(detailPathToAnalyse);
            File[] filesList = folderToAnalyse.listFiles();
            for (File file : filesList) {
              String fileName = file.getName();
              String mimeType = FileUtil.getMimeType(fileName);
              SimpleDocument document = null;
              if (isVersioned) {
                document = attachmentService.findExistingDocument(new SimpleDocumentPK(null,
                    componentId), fileName, new ForeignPK(publicationId), null);
                if (documentExists(document)) {
                  attachmentService.updateAttachment(document, file, false, false);
                  UnlockContext unlockContext = new UnlockContext(document.getId(), "", null, "");
                  unlockContext.addOption(UnlockOption.UPLOAD);
                  if (!document.isPublic()) {
                    unlockContext.addOption(UnlockOption.PRIVATE_VERSION);
                  }
                  attachmentService.unlock(unlockContext);
                }
              }
              if (!documentExists(document)) {
                String userId = "0";
                DocumentType documentType = DocumentType.attachment;
                SimpleDocument documentSource = getSourceDocument(fileName, new ForeignPK(
                    publicationId, componentId));
                if (documentSource != null) {
                  userId = documentSource.getCreatedBy();
                  documentType = documentSource.getDocumentType();
                }
                document = new SimpleDocument(new SimpleDocumentPK(null, componentId),
                    publicationId, 0, isVersioned, new SimpleAttachment(fileName, null,
                        null, null, file.length(), mimeType, userId, new Date(), null));
                document.setDocumentType(documentType);
                attachmentService.createAttachment(document, file, false);
              }
            }
            FileUtils.deleteQuietly(new File(resultActifyFullPath));
          }
        }
      }

    };
  }

  protected Job getActifyDocumentCleaner() {
    return new Job("PurgeActify") {

      @Override
      public void execute(JobExecutionContext context) throws Exception {
        logger.info("Purge of the source directory used by Actify...");
        int delayBeforePurge = ActifyDocumentProcessor.getDelayBeforePurge();
        long now = new Date().getTime();

        File folderToAnalyse = new File(ActifyDocumentProcessor.getActifySourcePath());
        File[] elementsList = folderToAnalyse.listFiles();

        // List all folders in Actify
        for (File element : elementsList) {
          long lastModified = element.lastModified();
          if (element.isDirectory()
              && lastModified + delayBeforePurge * 1000 * 60 < now) {
            FileUtils.deleteQuietly(new File(element.getAbsolutePath()));
          }
        }
      }
    };
  }

  private boolean isManagedBySilverpeas(File directory) {
    String directoryName = directory.getName();
    return directory.isDirectory() && (directoryName.startsWith("a_") || directoryName.
        startsWith("v_"));
  }

  private boolean documentExists(SimpleDocument document) {
    return document != null;
  }

  private SimpleDocument getSourceDocument(String filename, ForeignPK publication) {
    SimpleDocument source = null;
    SimpleDocumentList<SimpleDocument> documents = AttachmentServiceProvider.getAttachmentService().
        listDocumentsByForeignKey(publication, null);
    for (SimpleDocument aDocument : documents) {
      String destfile = FilenameUtils.getBaseName(filename);
      String srcfile = FilenameUtils.getBaseName(aDocument.getFilename());
      if (ActifyDocumentProcessor.isCADDocumentSupported(aDocument.getFilename()) && srcfile.equals(
          destfile)) {
        source = aDocument;
        break;
      }
    }
    return source;
  }

}