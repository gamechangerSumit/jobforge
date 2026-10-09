/**
 * Module {@code interview} (Dev 1): scheduling, rescheduling, cancelling, seeker response and completion of interviews
 * (API_CONTRACT §12.7, DATABASE_SCHEMA §3.5 / §4.5). Layout: api / app / domain / infra / events.
 * Other modules never reach into it; it reaches other modules only through their {@code facade} packages
 * (application, job, user, company, profile).
 */
package com.jobforge.backend.interview;
