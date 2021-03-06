/*
 * Copyright (C) 2000 - 2018 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have received a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/docs/core/legal/floss_exception.html"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.silverpeas.core.reminder;

import org.silverpeas.core.admin.user.model.User;
import org.silverpeas.core.contribution.model.ContributionIdentifier;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A reminder about any contribution that is triggered at a specified date time.
 * @author mmoquillon
 */
@Entity
@DiscriminatorValue("datetime")
public class DateTimeReminder extends Reminder {

  @Transient
  private transient OffsetDateTime dateTime;

  /**
   * Constructs a new reminder about the specified contribution and for the given user.
   * @param contributionId the unique identifier of the contribution.
   * @param user the user aimed by this reminder.
   */
  public DateTimeReminder(final ContributionIdentifier contributionId, final User user) {
    super(contributionId, user);
  }

  /**
   * Empty constructors for the persistence engine.
   */
  protected DateTimeReminder() {
    super();
  }

  @Override
  public final DateTimeReminder withText(final String text) {
    return super.withText(text);
  }


  /**
   * Triggers this reminder at the specified date time. The timezone of the specified date time
   * will be set in the timezone of the user behind this reminder.
   * @param dateTime the date time at which this reminder will be triggered once scheduled.
   * @return itself.
   */
  public DateTimeReminder triggerAt(final OffsetDateTime dateTime) {
    final ZoneId userZoneId = User.getById(getUserId()).getUserPreferences().getZoneId();
    this.dateTime = dateTime.atZoneSameInstant(userZoneId).toOffsetDateTime();
    return this;
  }

  /**
   * Gets the datetime at which the trigger of this reminder is set. The returned datetime is
   * based upon the timezone of the user behind this reminder.
   * @return the datetime of this reminder's trigger.
   */
  public OffsetDateTime getDateTime() {
    return isScheduled() ? getScheduledDateTime() : dateTime;
  }

  /**
   * This reminder is schedulable if the triggering date is defined and is after now.
   * @return true if the triggering date is after now, false otherwise.
   */
  @Override
  public boolean isSchedulable() {
    final OffsetDateTime triggeringDate = getDateTime();
    return triggeringDate != null && !triggeringDate.isBefore(OffsetDateTime.now());
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  protected OffsetDateTime computeTriggeringDate() {
    return dateTime;
  }
}
  